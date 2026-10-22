package com.charging.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.charging.common.annotation.Idempotent;
import com.charging.common.enums.OrderStatus;
import com.charging.common.exception.BusinessException;
import com.charging.dto.StartChargingDTO;
import com.charging.dto.StopChargingDTO;
import com.charging.entity.Charger;
import com.charging.entity.Gun;
import com.charging.entity.Order;
import com.charging.entity.User;
import com.charging.service.ChargerService;
import com.charging.service.ChargingService;
import com.charging.service.GunService;
import com.charging.service.OrderService;
import com.charging.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 充电业务实现
 *
 * <p><b>三层并发防护</b>（缺一不可，面试常被追问的就是"为什么有锁还要 CAS"）：
 * <ol>
 *   <li>幂等切面({@link Idempotent})：拦住同一用户的重复提交，防止重复下单</li>
 *   <li>Redisson 分布式锁：多实例部署下串行化"选枪 → 占枪 → 建单"整段逻辑，锁在事务外层</li>
 *   <li>数据库 CAS（{@code WHERE status = 期望值}）：最后一道正确性防线；
 *       Redis 挂掉、锁过期、时钟漂移等情况仍不会超卖</li>
 * </ol>
 *
 * <p>加锁顺序统一为 <b>充电桩 → 充电枪</b>，stop 亦同，避免交叉加锁导致死锁。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargingServiceImpl implements ChargingService {

    /**
     * 充电桩级锁：扫码充电只带 chargerId，锁桩天然覆盖"选枪"过程
     */
    private static final String CHARGER_LOCK_KEY = "lock:charging:charger:";

    /**
     * 订单级锁：停止充电以订单为粒度，粒度更细
     */
    private static final String ORDER_LOCK_KEY = "lock:charging:order:";

    /**
     * 抢锁最长等待时间(秒)。超过说明系统繁忙，快速失败优于长阻塞
     */
    private static final long LOCK_WAIT_SECONDS = 3L;

    private final OrderService orderService;
    private final ChargerService chargerService;
    private final GunService gunService;
    private final UserService userService;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;

    /**
     * 开始充电
     *
     * <p>幂等键取 {@code userId:chargerId}：同一用户 10 秒内对同一台桩的重复点击直接拒绝，
     * 既不会误伤"换一台桩再扫"，也能挡住前端连点。
     */
    @Override
    @Idempotent(prefix = "order:start", key = "#userId + ':' + #dto.chargerId", ttl = 10)
    public Order startCharging(Long userId, StartChargingDTO dto) {
        // 锁桩不锁枪：枪是在锁内选出来的，先锁枪等于锁了个还不存在的目标
        return withLock(CHARGER_LOCK_KEY + dto.getChargerId(),
                () -> transactionTemplate.execute(status -> doStart(userId, dto)));
    }

    @Override
    @Idempotent(prefix = "order:stop", key = "#dto.orderId", ttl = 10)
    public Order stopCharging(Long userId, StopChargingDTO dto) {
        return withLock(ORDER_LOCK_KEY + dto.getOrderId(),
                () -> transactionTemplate.execute(status -> doStop(userId, dto)));
    }

    /**
     * 在分布式锁保护下执行，锁的获取与释放都在事务之外
     *
     * <p>Redis 不可用时降级为直接执行：数据库 CAS 仍能保证不超卖，
     * 只是失去了跨实例串行化能力，优先保可用性。
     */
    private <T> T withLock(String lockKey, Supplier<T> action) {
        RLock lock;
        boolean acquired = false;
        try {
            lock = redissonClient.getLock(lockKey);
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统繁忙，请稍后重试");
        } catch (Exception e) {
            // 连接异常/Redis 宕机：降级放行，由数据库 CAS + 唯一索引兜底
            log.warn("分布式锁不可用，降级为数据库 CAS, lockKey={}", lockKey, e);
            return action.get();
        }

        if (!acquired) {
            throw new BusinessException("当前操作人数较多，请稍后重试");
        }
        try {
            return action.get();
        } finally {
            try {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            } catch (Exception e) {
                // 锁会随 watchdog 超时自动释放，此处仅记录
                log.warn("分布式锁释放失败, lockKey={}", lockKey, e);
            }
        }
    }

    /**
     * 开始充电（事务内执行，调用方必须已持有充电桩锁）
     */
    private Order doStart(Long userId, StartChargingDTO dto) {
        // 锁定用户行，串行化同一用户的并发下单，并校验账号状态
        User user = userService.lambdaQuery()
                .eq(User::getId, userId)
                .last("FOR UPDATE")
                .one();
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用，无法充电");
        }

        // 校验充电桩
        Charger charger = chargerService.getById(dto.getChargerId());
        if (charger == null) {
            throw new BusinessException("充电桩不存在");
        }
        if (charger.getStatus() == null || charger.getStatus() == 0) {
            throw new BusinessException("充电桩故障，无法使用");
        }
        if (charger.getStatus() == 2) {
            throw new BusinessException("充电桩使用中，请选择其他充电桩");
        }

        // 校验充电枪：未指定时自动选择该桩下第一把空闲枪(扫码场景只带 chargerId)
        Gun gun;
        if (dto.getGunId() != null) {
            gun = gunService.getById(dto.getGunId());
            if (gun == null) {
                throw new BusinessException("充电枪不存在");
            }
            if (!charger.getId().equals(gun.getChargerId())) {
                throw new BusinessException("该充电枪不属于此充电桩");
            }
            if (gun.getStatus() == null || gun.getStatus() == 0) {
                throw new BusinessException("充电枪故障，无法使用");
            }
            if (gun.getStatus() == 2) {
                throw new BusinessException("充电枪使用中，请选择其他充电枪");
            }
        } else {
            gun = gunService.lambdaQuery()
                    .eq(Gun::getChargerId, charger.getId())
                    .eq(Gun::getStatus, 1)
                    .orderByAsc(Gun::getCode)
                    .last("LIMIT 1")
                    .one();
            if (gun == null) {
                throw new BusinessException("该充电桩暂无空闲充电枪");
            }
        }

        // 检查用户是否已有充电中的订单
        long chargingCount = orderService.lambdaQuery()
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, OrderStatus.CHARGING.getCode())
                .count();
        if (chargingCount > 0) {
            throw new BusinessException("您有正在进行的充电订单，请先结束");
        }

        // CAS 锁定充电桩(仅当仍为空闲时更新成功)
        boolean chargerLocked = chargerService.lambdaUpdate()
                .eq(Charger::getId, charger.getId())
                .eq(Charger::getStatus, 1)
                .set(Charger::getStatus, 2)
                .update();
        if (!chargerLocked) {
            throw new BusinessException("充电桩已被占用，请选择其他充电桩");
        }

        // CAS 锁定充电枪，失败则抛异常回滚(释放上面锁定的充电桩)，防止并发抢占同一把枪
        boolean gunLocked = gunService.lambdaUpdate()
                .eq(Gun::getId, gun.getId())
                .eq(Gun::getStatus, 1)
                .set(Gun::getStatus, 2)
                .update();
        if (!gunLocked) {
            throw new BusinessException("充电枪已被占用，请选择其他充电枪");
        }

        // 生成订单编号: ORD + 时间戳 + 4位随机数
        String orderNo = "ORD" + System.currentTimeMillis() + RandomUtil.randomNumbers(4);

        // 创建订单(站点/桩/枪以服务端查询结果为准，不信任前端传入)
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setStationId(charger.getStationId());
        order.setChargerId(charger.getId());
        order.setGunId(gun.getId());
        order.setStartTime(LocalDateTime.now());
        order.setPrice(charger.getPrice());
        order.setPayType(dto.getPayType() != null ? dto.getPayType() : 1);
        order.setStatus(OrderStatus.CHARGING.getCode());
        orderService.save(order);

        log.info("开始充电, userId={}, chargerId={}, gunId={}, orderNo={}",
                userId, charger.getId(), gun.getId(), orderNo);
        return order;
    }

    /**
     * 停止充电并结算（事务内执行，调用方必须已持有订单锁）
     */
    private Order doStop(Long userId, StopChargingDTO dto) {
        // 行锁读取订单，串行化同一订单的并发结算，防止重复扣款
        Order order = orderService.lambdaQuery()
                .eq(Order::getId, dto.getOrderId())
                .last("FOR UPDATE")
                .one();
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此订单");
        }
        if (OrderStatus.of(order.getStatus()) != OrderStatus.CHARGING) {
            throw new BusinessException("订单状态不允许停止充电");
        }

        // 计算充电时长(分钟)
        LocalDateTime endTime = LocalDateTime.now();
        long minutes = Duration.between(order.getStartTime(), endTime).toMinutes();
        if (minutes < 1) {
            minutes = 1;
        }

        // 获取充电桩信息计算电量
        Charger charger = chargerService.getById(order.getChargerId());
        if (charger == null || charger.getPower() == null || charger.getPower().compareTo(BigDecimal.ZERO) <= 0) {
            // 计费参数缺失时不允许按 0 元结算，否则等于免费充电
            throw new BusinessException("充电桩计费参数异常，请联系管理员处理");
        }
        // 电量(度) = 功率(kW) * 时间(h)
        BigDecimal hours = new BigDecimal(minutes).divide(new BigDecimal("60"), 4, RoundingMode.HALF_UP);
        BigDecimal electricity = charger.getPower().multiply(hours).setScale(2, RoundingMode.HALF_UP);
        // 总金额 = 电量 * 单价
        BigDecimal price = order.getPrice() != null ? order.getPrice() : charger.getPrice();
        BigDecimal totalAmount = electricity.multiply(price).setScale(2, RoundingMode.HALF_UP);

        order.setEndTime(endTime);
        order.setDuration((int) minutes);
        order.setElectricity(electricity);
        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount);

        Integer payType = dto.getPayType() != null ? dto.getPayType() : order.getPayType();
        // 微信支付通道未接入：置为"待支付"会产生永远无法结清的订单，先显式拒绝
        if (payType != null && payType == 2) {
            throw new BusinessException("暂不支持微信支付，请使用余额支付");
        }
        order.setPayType(payType);

        if (payType != null && payType == 1) {
            // 原子扣减余额(数据库层面扣减)，余额不足则失败并回滚整个事务
            boolean deducted = userService.deductBalance(userId, totalAmount);
            if (!deducted) {
                throw new BusinessException("余额不足，请充值或选择其他支付方式");
            }
            // 状态流转经状态机校验：充电中 → 已完成
            OrderStatus.transit(order, OrderStatus.COMPLETED);
        } else {
            // 充电中 → 待支付(支付回调接入后再流转到已完成)
            OrderStatus.transit(order, OrderStatus.UNPAID);
        }
        // 乐观锁更新：返回 false 说明版本已被其它事务修改，必须中断，不能静默放过
        if (!orderService.updateById(order)) {
            throw new BusinessException("订单已被其他操作修改，请刷新后重试");
        }

        // CAS 恢复充电桩状态: 只有"使用中"才能恢复"空闲"，防止并发重复停止导致状态错乱
        boolean chargerReleased = chargerService.lambdaUpdate()
                .eq(Charger::getId, order.getChargerId())
                .eq(Charger::getStatus, 2)
                .set(Charger::getStatus, 1)
                .update();
        if (!chargerReleased) {
            throw new BusinessException("充电桩状态异常，停止充电失败");
        }

        // CAS 恢复充电枪状态
        boolean gunReleased = gunService.lambdaUpdate()
                .eq(Gun::getId, order.getGunId())
                .eq(Gun::getStatus, 2)
                .set(Gun::getStatus, 1)
                .update();
        if (!gunReleased) {
            throw new BusinessException("充电枪状态异常，停止充电失败");
        }

        log.info("停止充电, orderId={}, minutes={}, electricity={}, amount={}, status={}",
                order.getId(), minutes, electricity, totalAmount, order.getStatus());
        return order;
    }
}

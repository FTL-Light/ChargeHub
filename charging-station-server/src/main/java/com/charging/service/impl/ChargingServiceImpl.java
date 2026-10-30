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
import com.charging.mq.OrderDelayProducer;
import com.charging.service.ChargerService;
import com.charging.service.ChargingService;
import com.charging.service.GunService;
import com.charging.service.OrderService;
import com.charging.service.OrderSettlementService;
import com.charging.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * 充电业务实现
 *
 * <p><b>两层并发防护</b>：
 * <ol>
 *   <li>幂等切面({@link Idempotent})：拦住同一用户的重复提交，防止重复下单</li>
 *   <li>数据库兜底：事务保证四表原子化，桩枪状态用 CAS（{@code WHERE status = 期望值}）互斥，
 *       余额用原子 SQL 扣减 —— 正确性只依赖数据库，不依赖任何外部组件</li>
 * </ol>
 *
 * <p><b>为什么没有上分布式锁</b>：当前单实例部署，{@code synchronized} 与分布式锁效果等价而更轻；
 * 且真正挡住并发的是 CAS 与 {@code FOR UPDATE} 行锁，锁只是性能层。
 * 将来水平扩容时再引入，锁键仍取 {@code chargerId}，粒度保持单桩。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargingServiceImpl implements ChargingService {

    private final OrderService orderService;
    private final ChargerService chargerService;
    private final GunService gunService;
    private final UserService userService;
    private final OrderSettlementService orderSettlementService;
    private final OrderDelayProducer orderDelayProducer;
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
        return transactionTemplate.execute(status -> doStart(userId, dto));
    }

    @Override
    @Idempotent(prefix = "order:stop", key = "#dto.orderId", ttl = 10)
    public Order stopCharging(Long userId, StopChargingDTO dto) {
        return transactionTemplate.execute(status -> doStop(userId, dto));
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

        // 投递"充电超时自动结算"延迟消息。
        // 这条消息是整个卡单问题的关键：用户忘记点结束、App 被杀、网络抖动时，
        // 没有人会再触发结算，设备会被永久占用。
        // 注意这里是事务提交后才真正发送（见 OrderDelayProducer），避免回滚后消息已出。
        orderDelayProducer.publishChargingTimeout(order.getId(), orderNo);

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

        // 计费、扣款、释放设备统一交给结算服务：
        // 超时自动结算走的是同一个实现，保证手动结束和自动结束的账单口径完全一致。
        // 余额不足/参数异常会在服务内部抛异常，整个事务回滚。
        return orderSettlementService.settleByUser(order, LocalDateTime.now(), dto.getPayType());
    }
}

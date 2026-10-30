package com.charging.service.impl;

import com.charging.common.enums.OrderSettleType;
import com.charging.common.enums.OrderStatus;
import com.charging.common.exception.BusinessException;
import com.charging.entity.Order;
import com.charging.mq.OrderDelayProducer;
import com.charging.service.OrderService;
import com.charging.service.OrderSettlementService;
import com.charging.service.OrderTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时处理服务实现
 *
 * <p><b>并发互斥为什么不走 Redisson 锁？</b>
 * 这里用 {@code SELECT ... FOR UPDATE} 的行锁就够了：订单维度互斥本来就可以交给数据库，
 * 少一个 Redis 依赖意味着 Redis 故障时超时收敛能力不受影响——
 * 而超时收敛恰恰是最不能在大促/故障时掉链子的能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTimeoutServiceImpl implements OrderTimeoutService {

    private final OrderService orderService;
    private final OrderSettlementService orderSettlementService;
    private final OrderDelayProducer orderDelayProducer;
    private final TransactionTemplate transactionTemplate;

    /** 单次充电最长时长(分钟) */
    @Value("${order.max-charging-minutes:720}")
    private long maxChargingMinutes;

    /** 待支付订单保留时长(分钟) */
    @Value("${order.unpaid-timeout-minutes:15}")
    private long unpaidTimeoutMinutes;

    @Override
    public Order settleChargingTimeout(Long orderId, int settleType) {
        return transactionTemplate.execute(status -> {
            // 行锁读取：并发的多个触发源（MQ + 定时任务）在此串行
            Order order = orderService.lambdaQuery()
                    .eq(Order::getId, orderId)
                    .last("FOR UPDATE")
                    .one();
            if (order == null) {
                log.warn("超时结算跳过：订单不存在, orderId={}", orderId);
                return null;
            }
            if (OrderStatus.of(order.getStatus()) != OrderStatus.CHARGING) {
                // 幂等：用户已手动结束或被其他实例处理过
                log.info("超时结算跳过：订单已不在充电中, orderId={}, status={}", orderId, order.getStatus());
                return order;
            }

            // 结算截止到 SLA 边界：startTime + maxChargingMinutes，
            // 与用户实际离场时间无关（系统不知道用户何时拔枪），按规则封顶计费更公平。
            LocalDateTime deadline = order.getStartTime().plusMinutes(maxChargingMinutes);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime endTime = deadline.isBefore(now) ? deadline : now;

            Order settled = orderSettlementService.settleByTimeout(order, endTime, settleType);

            // 余额不足等情况下订单会转为"待支付"，需要再挂一条关单延迟消息，否则又是新的死单
            if (OrderStatus.of(settled.getStatus()) == OrderStatus.UNPAID) {
                orderDelayProducer.publishUnpaidClose(settled.getId(), settled.getOrderNo());
            }
            return settled;
        });
    }

    @Override
    public boolean closeUnpaidOrder(Long orderId) {
        Boolean closed = transactionTemplate.execute(status -> {
            Order order = orderService.lambdaQuery()
                    .eq(Order::getId, orderId)
                    .last("FOR UPDATE")
                    .one();
            if (order == null || OrderStatus.of(order.getStatus()) != OrderStatus.UNPAID) {
                log.info("关单跳过：订单不存在或不处于待支付, orderId={}", orderId);
                return false;
            }
            LocalDateTime expiredAt = order.getUpdateTime() != null
                    ? order.getUpdateTime().plusMinutes(unpaidTimeoutMinutes)
                    : order.getCreateTime().plusMinutes(unpaidTimeoutMinutes);
            if (expiredAt.isAfter(LocalDateTime.now())) {
                // 消息提前到达（时钟漂移/手工触发），延迟尚未到期，不关单
                log.info("关单跳过：支付时限未到, orderId={}, expiredAt={}", orderId, expiredAt);
                return false;
            }

            OrderStatus.transit(order, OrderStatus.CANCELLED);
            if (!orderService.updateById(order)) {
                throw new BusinessException("订单已被其他操作修改，请刷新后重试");
            }
            // 二次保证：结算阶段若因异常没释放成功，关单时再兜一次
            orderSettlementService.releaseResourcesIfOccupied(order);
            log.info("待支付订单超时关单, orderId={}, settleType={}", orderId, order.getSettleType());
            return true;
        });
        return Boolean.TRUE.equals(closed);
    }

    @Override
    public int compensateExpiredOrders(int settleType, int batchSize) {
        int handled = 0;
        LocalDateTime now = LocalDateTime.now();

        List<Order> timeoutOrders = findStuckChargingOrders(batchSize);
        for (Order order : timeoutOrders) {
            try {
                Order settled = settleChargingTimeout(order.getId(), settleType);
                if (settled != null && OrderStatus.of(settled.getStatus()) != OrderStatus.CHARGING) {
                    handled++;
                }
            } catch (BusinessException e) {
                // 业务异常（如乐观锁冲突）本轮不处理，下一轮扫描会自动重试
                log.warn("兜底结算未成功, orderId={}, 原因={}", order.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("兜底结算异常, orderId={}", order.getId(), e);
            }
        }

        List<Order> unpaidOrders = orderService.lambdaQuery()
                .eq(Order::getStatus, OrderStatus.UNPAID.getCode())
                .lt(Order::getUpdateTime, now.minusMinutes(unpaidTimeoutMinutes))
                .orderByAsc(Order::getId)
                .last("LIMIT " + batchSize)
                .list();
        for (Order order : unpaidOrders) {
            try {
                if (closeUnpaidOrder(order.getId())) {
                    handled++;
                }
            } catch (Exception e) {
                log.error("兜底关单异常, orderId={}", order.getId(), e);
            }
        }

        if (handled > 0) {
            log.info("超时兜底任务处理订单 {} 条", handled);
        }
        return handled;
    }

    @Override
    public List<Order> findStuckChargingOrders(int batchSize) {
        return orderService.lambdaQuery()
                .eq(Order::getStatus, OrderStatus.CHARGING.getCode())
                .lt(Order::getStartTime, LocalDateTime.now().minusMinutes(maxChargingMinutes))
                .orderByAsc(Order::getId)
                .last("LIMIT " + batchSize)
                .list();
    }
}

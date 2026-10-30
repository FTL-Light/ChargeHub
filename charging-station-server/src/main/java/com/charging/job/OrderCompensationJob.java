package com.charging.job;

import com.charging.common.enums.OrderSettleType;
import com.charging.common.enums.OrderStatus;
import com.charging.entity.Order;
import com.charging.mq.OrderDelayProducer;
import com.charging.service.OrderService;
import com.charging.service.OrderTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时兜底任务
 *
 * <p><b>为什么有了延迟消息还要定时任务？</b>
 * 因为消息链路每一环都可能失效：Broker 宕机期间消息没发出去、消息磁盘写失败、
 * 消费者 GC 停顿导致 ACK 超时、延迟消息还在队列里服务就重启且队列没开持久化……
 * 依赖单一手段收敛资金与设备状态是不可接受的。
 * <b>数据库扫描是唯一可信的事实来源</b>，延迟消息只是让它变得更快、更实时。
 *
 * <p>两者是互补而非替代关系：
 * <ul>
 *   <li>正常情况下：延迟消息毫秒级触发，扫描任务无事可做（查询结果为空）</li>
 *   <li>异常情况下：扫描任务在最坏一个扫描周期内补上，保证最终一致</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCompensationJob {

    private final OrderService orderService;
    private final OrderTimeoutService orderTimeoutService;
    private final OrderDelayProducer orderDelayProducer;

    @Value("${order.compensation.enabled:true}")
    private boolean enabled;

    @Value("${order.compensation.batch-size:200}")
    private int batchSize;

    @Value("${order.max-charging-minutes:720}")
    private long maxChargingMinutes;

    @Value("${order.unpaid-timeout-minutes:15}")
    private long unpaidTimeoutMinutes;

    /**
     * 兜底扫描：处理已超时但仍未收敛的订单
     *
     * <p>fixedDelay 而非 fixedRate：上一轮没跑完不启动下一轮，避免堆积。
     */
    @Scheduled(fixedDelayString = "${order.compensation.fixed-delay-ms:60000}")
    public void compensateExpiredOrders() {
        if (!enabled) {
            return;
        }
        int handled = orderTimeoutService.compensateExpiredOrders(
                OrderSettleType.COMPENSATION.getCode(), batchSize);
        if (handled > 0) {
            log.warn("检测到 {} 条订单未被延迟消息收敛，已由兜底任务处理", handled);
        }
    }

    /**
     * 启动后立即结算超期订单，并为未超期订单补齐延迟消息
     *
     * <p>解决"MQ 宕机期间产生的订单永远收不到超时结算"的问题：
     * 服务重启 → 消息早已丢失 → 没有 @EventListener 补偿的话，这些订单会一直卡着
     * （此时连定时扫描都救不了尚未超期的订单，因为它们的延迟消息丢了且还没到扫描触发点）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void restorePendingTasks() {
        LocalDateTime now = LocalDateTime.now();

        // 1. 已超期的充电订单：立即结算
        List<Order> expired = orderTimeoutService.findStuckChargingOrders(batchSize);
        for (Order order : expired) {
            log.warn("启动补偿：发现超期未结算订单, orderId={}, startTime={}", order.getId(), order.getStartTime());
            orderTimeoutService.settleChargingTimeout(order.getId(), OrderSettleType.COMPENSATION.getCode());
        }

        // 2. 未超期的充电订单：按剩余时长补投延迟消息
        List<Order> charging = orderService.lambdaQuery()
                .eq(Order::getStatus, OrderStatus.CHARGING.getCode())
                .ge(Order::getStartTime, now.minusMinutes(maxChargingMinutes))
                .orderByAsc(Order::getId)
                .last("LIMIT " + batchSize)
                .list();
        for (Order order : charging) {
            long remainingMs = Duration.between(now, order.getStartTime().plusMinutes(maxChargingMinutes)).toMillis();
            orderDelayProducer.publishChargingTimeout(order.getId(), order.getOrderNo(), remainingMs);
        }

        // 3. 待支付订单：按剩余支付时限补投关单消息
        List<Order> unpaid = orderService.lambdaQuery()
                .eq(Order::getStatus, OrderStatus.UNPAID.getCode())
                .orderByAsc(Order::getId)
                .last("LIMIT " + batchSize)
                .list();
        for (Order order : unpaid) {
            LocalDateTime base = order.getUpdateTime() != null ? order.getUpdateTime() : order.getCreateTime();
            long remainingMs = Duration.between(now, base.plusMinutes(unpaidTimeoutMinutes)).toMillis();
            if (remainingMs <= 0) {
                orderTimeoutService.closeUnpaidOrder(order.getId());
            } else {
                orderDelayProducer.publishUnpaidClose(order.getId(), order.getOrderNo(), remainingMs);
            }
        }

        log.info("启动补偿完成：超期即结 {} 条，充电重投 {} 条，待支付重投 {} 条",
                expired.size(), charging.size(), unpaid.size());
    }
}

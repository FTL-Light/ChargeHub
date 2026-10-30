package com.charging.mq;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 订单延迟消息生产者
 *
 * <p>三个关键设计：
 * <ol>
 *   <li><b>事务提交后再投递</b>：如果在本事务内发消息，事务回滚时消息已经出去了，
 *       消费端会去结算一个根本不存在的订单。这里用 {@code afterCommit} 规避。</li>
 *   <li><b>生产者确认 + 有限重试</b>：消息没到达 Broker（nack / 超时无 ack）要能感知，
 *       重试几次仍失败就放弃——不无限重试，把最后的正确性交给定时兜底任务。</li>
 *   <li><b>失败不等于业务失败</b>：投递失败只记日志、返回 false，绝不抛异常回滚业务。
 *       MQ 是"加速"手段，不是唯一手段。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDelayProducer {

    /** 等待 Broker 确认的时间 */
    private static final long CONFIRM_TIMEOUT_SECONDS = 3L;

    /** 投递失败后的重试次数 */
    private static final int MAX_SEND_RETRY = 2;

    private final RabbitTemplate rabbitTemplate;

    @PostConstruct
    public void initCallbacks() {
        // 消息到达 Broker 但不可路由（交换机/队列不存在或路由键写错）的兜底告警
        rabbitTemplate.setReturnsCallback(returned -> log.error(
                "延迟消息不可路由, exchange={}, routingKey={}, replyText={}, message={}",
                returned.getExchange(), returned.getRoutingKey(),
                returned.getReplyText(), returned.getMessage()));
    }

    /**
     * 投递"充电超时自动结算"消息
     *
     * <p>方法返回不代表消息已投递：处在事务中时，真正的发送动作在事务提交后执行。
     *
     * @param orderId 订单ID
     * @param orderNo 订单号（仅用于日志）
     */
    public void publishChargingTimeout(Long orderId, String orderNo) {
        publish(orderId, orderNo, MqConstants.TYPE_CHARGING_TIMEOUT, MqConstants.RK_DELAY_TIMEOUT, null);
    }

    /**
     * 按剩余时长投递"充电超时自动结算"消息（服务重启后的补偿场景）
     *
     * <p>队列级 TTL 只能固定一个值，这里用<b>消息级过期时间</b>覆盖：
     * Broker 取"队列 TTL 与消息 TTL 中的较小值"，因此可以为重启时那些
     * 已经充了一半的订单设置准确的剩余延迟。
     *
     * @param orderId  订单ID
     * @param orderNo  订单号
     * @param delayMs  剩余延迟毫秒数，小于等于 0 时应由扫描任务立即处理
     */
    public void publishChargingTimeout(Long orderId, String orderNo, long delayMs) {
        if (delayMs <= 0) {
            log.info("剩余延迟已耗尽，跳过消息投递并交由兜底任务立即处理, orderId={}", orderId);
            return;
        }
        publish(orderId, orderNo, MqConstants.TYPE_CHARGING_TIMEOUT, MqConstants.RK_DELAY_TIMEOUT, delayMs);
    }

    /**
     * 投递"待支付订单自动关单"消息
     */
    public void publishUnpaidClose(Long orderId, String orderNo) {
        publish(orderId, orderNo, MqConstants.TYPE_UNPAID_CLOSE, MqConstants.RK_DELAY_UNPAID, null);
    }

    /**
     * 按剩余时长投递"待支付关单"消息（重启补偿场景）
     */
    public void publishUnpaidClose(Long orderId, String orderNo, long delayMs) {
        if (delayMs <= 0) {
            log.info("支付时限已过，跳过消息投递并交由兜底任务立即处理, orderId={}", orderId);
            return;
        }
        publish(orderId, orderNo, MqConstants.TYPE_UNPAID_CLOSE, MqConstants.RK_DELAY_UNPAID, delayMs);
    }

    /**
     * 事务提交后投递；不在事务中则立即投递
     */
    private void publish(Long orderId, String orderNo, String type, String routingKey, Long delayMs) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendWithConfirm(orderId, orderNo, type, routingKey, delayMs);
                }
            });
        } else {
            sendWithConfirm(orderId, orderNo, type, routingKey, delayMs);
        }
    }

    /**
     * 带确认的发送，失败重试有限次
     *
     * @return true 表示已收到 Broker 的 ack
     */
    private boolean sendWithConfirm(Long orderId, String orderNo, String type, String routingKey, Long delayMs) {
        OrderDelayMessage message = OrderDelayMessage.builder()
                .orderId(orderId)
                .orderNo(orderNo)
                .type(type)
                .publishTime(LocalDateTime.now())
                .build();

        for (int attempt = 1; attempt <= MAX_SEND_RETRY; attempt++) {
            try {
                CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
                MessagePostProcessor processor = msg -> {
                    if (delayMs != null) {
                        // 消息级 TTL：覆盖队列默认值，用于补偿场景的剩余延迟
                        msg.getMessageProperties().setExpiration(String.valueOf(delayMs));
                    }
                    return msg;
                };
                rabbitTemplate.convertAndSend(MqConstants.DELAY_EXCHANGE, routingKey, message,
                        processor, correlationData);
                var confirm = correlationData.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (confirm != null && confirm.isAck()) {
                    log.info("延迟消息投递成功, orderId={}, type={}, attempt={}", orderId, type, attempt);
                    return true;
                }
                log.warn("延迟消息未被确认, orderId={}, type={}, attempt={}, confirm={}",
                        orderId, type, attempt, confirm);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("等待消息确认被中断, orderId={}, type={}", orderId, type);
                return false;
            } catch (Exception e) {
                log.warn("延迟消息投递异常, orderId={}, type={}, attempt={}", orderId, type, attempt, e);
            }
        }
        // 彻底失败：交由定时兜底任务按数据库扫描收敛，这里只告警
        log.error("延迟消息投递最终失败，等待定时任务兜底, orderId={}, type={}", orderId, type);
        return false;
    }
}

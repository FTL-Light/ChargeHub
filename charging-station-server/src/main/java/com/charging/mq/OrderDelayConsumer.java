package com.charging.mq;

import com.charging.common.enums.OrderSettleType;
import com.charging.entity.Order;
import com.charging.service.OrderTimeoutService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 订单延迟消息消费者
 *
 * <p>四条可靠性约定：
 * <ol>
 *   <li><b>手动 ACK</b>：业务真正成功才 {@code basicAck}，
 *       否则进程崩溃时消息会被当作已消费而永久丢失。</li>
 *   <li><b>幂等</b>：先查订单状态，已收敛的直接 ACK 返回；重复投递不产生副作用。</li>
 *   <li><b>有限重试</b>：失败后<b>重发一条带递增计数的新消息</b>并 ACK 原消息，
 *       超过阈值才 nack 进死信队列。注意这里不能直接 {@code basicNack(requeue=true)}：
 *       重投的消息 headers 不会变，计数永远加不上去，会形成无限循环。</li>
 *   <li><b>最终兜底</b>：即使进入死信队列也不会卡单，定时任务按数据库扫描收敛。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDelayConsumer {

    /** 最大消费重试次数，超过则转入死信队列等待兜底与人工排查 */
    private static final int MAX_RETRY = 3;

    private final OrderTimeoutService orderTimeoutService;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 结算队列监听：充电超时自动结算 + 待支付超时关单共用一条队列
     */
    @RabbitListener(queues = MqConstants.SETTLE_QUEUE, ackMode = "MANUAL")
    public void handleSettle(OrderDelayMessage body, Message message, Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (body == null || body.getOrderId() == null) {
            // 格式非法的消息无法修复，ACK 丢弃并记录，避免毒丸阻塞队列
            log.error("延迟消息格式非法，已丢弃, raw={}", message.getBody());
            channel.basicAck(deliveryTag, false);
            return;
        }
        try {
            logDelayAccuracy(body);
            dispatch(body);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            handleFailure(body, message, channel, deliveryTag, e);
        }
    }

    private void dispatch(OrderDelayMessage body) {
        if (MqConstants.TYPE_CHARGING_TIMEOUT.equals(body.getType())) {
            Order order = orderTimeoutService.settleChargingTimeout(
                    body.getOrderId(), OrderSettleType.AUTO_TIMEOUT.getCode());
            if (order != null) {
                log.info("充电超时自动结算完成, orderId={}, status={}, amount={}",
                        order.getId(), order.getStatus(), order.getPayAmount());
            }
        } else if (MqConstants.TYPE_UNPAID_CLOSE.equals(body.getType())) {
            orderTimeoutService.closeUnpaidOrder(body.getOrderId());
        } else {
            log.warn("未知延迟消息类型, type={}, orderId={}", body.getType(), body.getOrderId());
        }
    }

    /**
     * 消费失败处理
     *
     * <p>生产环境更合理的做法是<b>独立的重试队列 + 指数退避</b>（避免瞬时故障被疯狂重试），
     * 这里受限于演示范围，用"立即重发 + 计数"实现等价的可观测重试策略。
     */
    private void handleFailure(OrderDelayMessage body, Message message, Channel channel,
                               long deliveryTag, Exception e) throws IOException {
        int retried = getRetryCount(message);
        log.error("处理延迟消息失败, orderId={}, type={}, retry={}", body.getOrderId(), body.getType(), retried, e);

        if (retried >= MAX_RETRY) {
            // requeue=false：消息进入 SETTLE_DLX_EXCHANGE → 死信队列，不再回灌业务队列
            channel.basicNack(deliveryTag, false, false);
            log.error("延迟消息重试 {} 次仍失败，已转入死信队列, orderId={}", retried, body.getOrderId());
            return;
        }
        republishWithRetry(body, retried + 1);
        channel.basicAck(deliveryTag, false);
    }

    /**
     * 重发重试消息：携带递增后的重试次数，路由直达业务队列（不再经过延迟队列）
     */
    private void republishWithRetry(OrderDelayMessage body, int nextRetry) {
        String routingKey = MqConstants.TYPE_UNPAID_CLOSE.equals(body.getType())
                ? MqConstants.RK_SETTLE_UNPAID
                : MqConstants.RK_SETTLE_TIMEOUT;
        MessagePostProcessor processor = msg -> {
            msg.getMessageProperties().setHeader(MqConstants.HEADER_RETRY_COUNT, nextRetry);
            return msg;
        };
        try {
            rabbitTemplate.convertAndSend(MqConstants.SETTLE_EXCHANGE, routingKey, body, processor);
            log.info("延迟消息重发, orderId={}, nextRetry={}", body.getOrderId(), nextRetry);
        } catch (Exception ex) {
            // 重发失败也无所谓：原消息已 ACK，订单会由定时兜底任务收敛
            log.error("延迟消息重发失败，等待定时任务兜底, orderId={}", body.getOrderId(), ex);
        }
    }

    private int getRetryCount(Message message) {
        Object retry = message.getMessageProperties().getHeader(MqConstants.HEADER_RETRY_COUNT);
        if (retry instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    /**
     * 观测实际延迟与投递时间的偏差，用于判断 Broker 是否有积压
     */
    private void logDelayAccuracy(OrderDelayMessage body) {
        if (body.getPublishTime() != null) {
            long delayedMinutes = Duration.between(body.getPublishTime(), LocalDateTime.now()).toMinutes();
            log.info("收到延迟消息, orderId={}, type={}, 投递于={}, 实际延迟={}分钟",
                    body.getOrderId(), body.getType(), body.getPublishTime(), delayedMinutes);
        }
    }
}

package com.charging.config;

import com.charging.mq.MqConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 延迟队列装配
 *
 * <p>采用「TTL + 死信交换机」实现延迟：<b>每种延迟等级一条独立延迟队列</b>。
 * 延迟队列本身<b>没有消费者</b>，消息在 TTL 到期后由 Broker 投递到死信交换机，
 * 再路由到真正的业务队列。
 */
@Slf4j
@Configuration
public class RabbitConfig {

    /** 单次充电最长时长(毫秒)，超过则触发自动结算 */
    @Value("${order.max-charging-minutes:720}")
    private long maxChargingMinutes;

    /** 待支付订单保留时长(毫秒) */
    @Value("${order.unpaid-timeout-minutes:15}")
    private long unpaidTimeoutMinutes;

    /**
     * 延迟交换机：接收生产者投递的新消息
     */
    @Bean
    public DirectExchange delayExchange() {
        return new DirectExchange(MqConstants.DELAY_EXCHANGE, true, false);
    }

    /**
     * 死信（延迟到期）交换机：延迟队列 TTL 到期后由 Broker 自动投递到这里
     */
    @Bean
    public DirectExchange settleExchange() {
        return new DirectExchange(MqConstants.SETTLE_EXCHANGE, true, false);
    }

    /**
     * 充电超时延迟队列：TTL = 单次充电上限，到期成为死信进入结算交换机
     */
    @Bean
    public Queue delayTimeoutQueue() {
        log.info("充电超时延迟队列 TTL = {} 分钟", maxChargingMinutes);
        return QueueBuilder.durable(MqConstants.DELAY_TIMEOUT_QUEUE)
                .ttl((int) (maxChargingMinutes * 60 * 1000L))
                .deadLetterExchange(MqConstants.SETTLE_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.RK_SETTLE_TIMEOUT)
                .build();
    }

    /**
     * 待支付超时延迟队列：TTL = 支付时限，到期成为死信进入结算交换机
     */
    @Bean
    public Queue delayUnpaidQueue() {
        log.info("待支付延迟队列 TTL = {} 分钟", unpaidTimeoutMinutes);
        return QueueBuilder.durable(MqConstants.DELAY_UNPAID_QUEUE)
                .ttl((int) (unpaidTimeoutMinutes * 60 * 1000L))
                .deadLetterExchange(MqConstants.SETTLE_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.RK_SETTLE_UNPAID)
                .build();
    }

    /**
     * 业务结算队列：真正被消费者监听
     *
     * <p>独立的死信交换机（{@link MqConstants#SETTLE_DLX_EXCHANGE}）用于接管重试耗尽的毒丸消息。
     * 这里<b>绝对不能</b>复用 {@link MqConstants#SETTLE_EXCHANGE}：业务队列死信后又回到同一个交换机
     * 会被重新路由回自己，形成无限循环。
     */
    @Bean
    public Queue settleQueue() {
        Map<String, Object> args = new HashMap<>(2);
        args.put("x-dead-letter-exchange", MqConstants.SETTLE_DLX_EXCHANGE);
        return new Queue(MqConstants.SETTLE_QUEUE, true, false, false, args);
    }

    @Bean
    public Binding delayTimeoutBinding() {
        return BindingBuilder.bind(delayTimeoutQueue()).to(delayExchange()).with(MqConstants.RK_DELAY_TIMEOUT);
    }

    @Bean
    public Binding delayUnpaidBinding() {
        return BindingBuilder.bind(delayUnpaidQueue()).to(delayExchange()).with(MqConstants.RK_DELAY_UNPAID);
    }

    @Bean
    public Binding settleTimeoutBinding() {
        return BindingBuilder.bind(settleQueue()).to(settleExchange()).with(MqConstants.RK_SETTLE_TIMEOUT);
    }

    @Bean
    public Binding settleUnpaidBinding() {
        return BindingBuilder.bind(settleQueue()).to(settleExchange()).with(MqConstants.RK_SETTLE_UNPAID);
    }

    /**
     * 重试耗尽后的死信交换机：扇出类型，无需关心路由键，消息直接落入死信队列
     */
    @Bean
    public FanoutExchange settleDlxExchange() {
        return new FanoutExchange(MqConstants.SETTLE_DLX_EXCHANGE, true, false);
    }

    /**
     * 重试耗尽后的死信队列：消息不自动消费，留作人工排查与告警依据。
     * 配合定时兜底任务，即使消息进了这里，订单最终仍会被数据库扫描兜住。
     */
    @Bean
    public Queue settleDeadQueue() {
        return QueueBuilder.durable(MqConstants.SETTLE_DEAD_QUEUE).build();
    }

    @Bean
    public Binding settleDeadBinding() {
        return BindingBuilder.bind(settleDeadQueue()).to(settleDlxExchange());
    }

    /**
     * JSON 序列化：可读性远好于 JDK 序列化，且跨语言没问题
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

package com.charging.mq;

/**
 * 订单延迟消息的交换机 / 队列 / 路由键常量
 *
 * <p><b>为什么用「TTL + 死信」而不是一条延迟队列配不同 TTL？</b>
 * 死信队列的 TTL 是队列级属性，若把 12 分钟的支付关单和 12 小时的充电超时放进同一个队列，
 * 短延迟消息会被队头的长延迟消息<b>阻塞</b>（RabbitMQ 只检查队头是否过期），导致关单消息迟迟不出队。
 * 所以这里<b>每种延迟等级单独一条延迟队列</b>，从根上避免队头阻塞。
 *
 * <p>另一条备选方案是 {@code rabbitmq_delayed_message_exchange} 插件（x-delayed-message），
 * 支持任意延迟且无需拆分队列；代价是依赖 Broker 插件，社区版默认不带。
 * 当前实现只需要一个原生 RabbitMQ（docker-compose 已提供），可移植性更好。
 *
 * <p>消息流向：
 * <pre>
 *   生产者 ──> order.delay.exchange
 *                ├─(delay.charging.timeout)─> order.delay.timeout.queue [TTL=maxChargingMinutes]
 *                └─(delay.payment.unpaid)──> order.delay.unpaid.queue   [TTL=unpaidTimeoutMinutes]
 *                                                   │ TTL 到期后成为死信
 *                                                   ↓
 *                        order.settle.exchange ──> order.settle.queue ──> 消费者结算/关单
 * </pre>
 */
public interface MqConstants {

    /** 接收新消息的延迟交换机 */
    String DELAY_EXCHANGE = "order.delay.exchange";

    /** 接收死信（延迟到期）的交换机 */
    String SETTLE_EXCHANGE = "order.settle.exchange";

    /** 充电超时延迟队列 */
    String DELAY_TIMEOUT_QUEUE = "order.delay.timeout.queue";

    /** 待支付超时延迟队列 */
    String DELAY_UNPAID_QUEUE = "order.delay.unpaid.queue";

    /** 实际被消费的业务队列 */
    String SETTLE_QUEUE = "order.settle.queue";

    /** 业务队列重试耗尽后转入的死信交换机（与延迟用的 SETTLE_EXCHANGE 分开，避免自己投递给自己） */
    String SETTLE_DLX_EXCHANGE = "order.settle.dlx.exchange";

    /** 重试耗尽后转入的死信队列（人工排查用，不自动消费） */
    String SETTLE_DEAD_QUEUE = "order.settle.dead.queue";

    /** 生产者 → 延迟队列：充电超时 */
    String RK_DELAY_TIMEOUT = "delay.charging.timeout";

    /** 生产者 → 延迟队列：待支付关单 */
    String RK_DELAY_UNPAID = "delay.payment.unpaid";

    /** 延迟队列 → 业务队列：超时结算 */
    String RK_SETTLE_TIMEOUT = "settle.charging.timeout";

    /** 延迟队列 → 业务队列：待支付关单 */
    String RK_SETTLE_UNPAID = "settle.payment.unpaid";

    /** 消息类型：充电超时自动结算 */
    String TYPE_CHARGING_TIMEOUT = "CHARGING_TIMEOUT";

    /** 消息类型：待支付订单自动关单 */
    String TYPE_UNPAID_CLOSE = "UNPAID_CLOSE";

    /** 消息头：重试次数 */
    String HEADER_RETRY_COUNT = "x-retry-count";
}

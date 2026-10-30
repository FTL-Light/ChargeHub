package com.charging.service;

import com.charging.entity.Order;

import java.util.List;

/**
 * 订单超时处理服务
 *
 * <p>负责两类超时收敛，彻底解决"订单永久充电中、枪永久占用"的卡单问题：
 * <ol>
 *   <li><b>充电超时</b>：超过 {@code order.max-charging-minutes} 未结束的订单，自动结算并释放设备</li>
 *   <li><b>支付超时</b>：处于"待支付"且超过 {@code order.unpaid-timeout-minutes} 的订单，自动关单</li>
 * </ol>
 *
 * <p>本服务由三种触发源调用，业务逻辑保持一致：
 * <ul>
 *   <li>RabbitMQ 延迟消息（主链路，秒级-分钟级精度）</li>
 *   <li>定时兜底任务（消息丢失/服务宕机期间的最后防线）</li>
 *   <li>应用启动后的消息重投补偿</li>
 * </ul>
 * 幂等由"行锁 + 状态机流转 + 乐观锁"保证：并发重复触发时，第二个执行者会看到非目标状态并直接跳过。
 */
public interface OrderTimeoutService {

    /**
     * 充电超时自动结算
     *
     * @param orderId    订单ID
     * @param settleType 触发来源，见 {@link com.charging.common.enums.OrderSettleType#getCode()}
     * @return 结算后的订单；订单不存在返回 null；已非"充电中"时原样返回（幂等）
     */
    Order settleChargingTimeout(Long orderId, int settleType);

    /**
     * 待支付订单超时关单
     *
     * @param orderId 订单ID
     * @return 是否实际执行了关单
     */
    boolean closeUnpaidOrder(Long orderId);

    /**
     * 扫描数据库中已超时但尚未收敛的订单
     *
     * @param settleType 结算来源标记
     * @param batchSize  单次处理上限
     * @return 本次处理的订单数量
     */
    int compensateExpiredOrders(int settleType, int batchSize);

    /**
     * 查询仍处于"充电中"且已超过最长充电时长的订单（监控与演示用）
     *
     * @param batchSize 查询上限
     * @return 卡单订单列表
     */
    List<Order> findStuckChargingOrders(int batchSize);
}

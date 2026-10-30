package com.charging.service;

import com.charging.entity.Order;

import java.time.LocalDateTime;

/**
 * 订单结算服务
 *
 * <p>把"充电结束 → 计费 → 扣款 → 释放设备"这段逻辑从 {@code ChargingServiceImpl} 抽出来，
 * 供<b>用户主动停充</b>和<b>超时自动结算</b>两条链路共用。
 *
 * <p>抽出来的原因不是"代码看起来更优雅"，而是：<b>计费口径必须唯一</b>。
 * 一旦两个地方各算一次，迟早出现"用户手动结束收 12 块、系统自动结算收 15 块"的资金类事故。
 *
 * <p>两条链路的关键差异（面试常问）：
 * <table>
 *   <tr><td></td><td>用户主动结算</td><td>超时自动结算</td></tr>
 *   <tr><td>余额不足</td><td>抛异常回滚整个事务，让用户去充值</td><td>转为"待支付"后照常释放设备</td></tr>
 *   <tr><td>计费参数缺失</td><td>抛异常拒绝结算</td><td>按最小计费口径兜底并释放设备</td></tr>
 *   <tr><td>设备已释放</td><td>视为成功（CAS 幂等）</td><td>同上</td></tr>
 * </table>
 * 核心原则：<b>自动链路永远不能因为业务异常把设备锁死</b>，
 * 少收一笔钱可以事后追缴，设备被永久占用则意味着这个桩彻底不可用。
 */
public interface OrderSettlementService {

    /**
     * 用户主动结束充电
     *
     * <p>严格模式：余额不足、计费参数异常都直接抛 {@code BusinessException} 回滚事务，
     * 不允许产生收不回来的账单。
     *
     * @param order    行锁中的订单（调用方必须已锁定该行）
     * @param endTime  结束时间
     * @param payType  支付方式，null 时沿用订单原值
     * @return 已落库的订单
     */
    Order settleByUser(Order order, LocalDateTime endTime, Integer payType);

    /**
     * 超时自动结算
     *
     * <p>收敛模式：任何异常都不能阻断"释放设备"这一步。
     *
     * @param order      行锁中的订单（调用方必须已锁定该行）
     * @param endTime    结束时间
     * @param settleType 结算来源，见 {@link com.charging.common.enums.OrderSettleType}
     * @return 已落库的订单；若订单已处于终态则原样返回
     */
    Order settleByTimeout(Order order, LocalDateTime endTime, int settleType);

    /**
     * 释放订单占用的设备（幂等）
     *
     * <p>设备已是"空闲"时直接返回；仅在设备仍处于"使用中"但释放失败时记错误日志。
     * 关单链路调用它做最后一道保证：即使结算阶段因异常没释放成功，关单时也要再试一次。
     *
     * @param order 订单
     */
    void releaseResourcesIfOccupied(Order order);
}

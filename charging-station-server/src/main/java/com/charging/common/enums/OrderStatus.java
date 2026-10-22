package com.charging.common.enums;

import com.charging.common.exception.BusinessException;
import com.charging.entity.Order;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态机
 *
 * <p>订单状态的合法流转集中收敛在此，任何业务代码都不允许直接 order.setStatus(...)，
 * 必须经过 {@link #transit(Order, OrderStatus)} 校验，避免出现
 * "已完成 → 充电中""已取消 → 待支付" 这类非法状态回退。
 *
 * <p>流转规则：
 * <pre>
 *   充电中(1) ──结算成功──> 已完成(3)
 *            ├─结算挂账──> 待支付(2) ──支付成功──> 已完成(3)
 *            └─异常/取消──> 已取消(0)
 *   已完成(3)、已取消(0) 为终态，不可再流转
 * </pre>
 */
public enum OrderStatus {

    CANCELLED(0, "已取消"),
    CHARGING(1, "充电中"),
    UNPAID(2, "待支付"),
    COMPLETED(3, "已完成");

    private final int code;
    private final String desc;

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS;

    static {
        Map<OrderStatus, Set<OrderStatus>> map = new EnumMap<>(OrderStatus.class);
        map.put(CHARGING, Set.of(COMPLETED, UNPAID, CANCELLED));
        map.put(UNPAID, Set.of(COMPLETED, CANCELLED));
        map.put(COMPLETED, Set.of());
        map.put(CANCELLED, Set.of());
        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 按状态码解析，未知状态返回 null
     */
    public static OrderStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断当前状态能否流转到目标状态
     */
    public boolean canTransitTo(OrderStatus target) {
        if (target == null) {
            return false;
        }
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /**
     * 校验并执行状态流转(唯一入口)
     *
     * @param order  订单实体
     * @param target 目标状态
     * @throws BusinessException 当前状态不允许流转到目标状态时抛出
     */
    public static void transit(Order order, OrderStatus target) {
        OrderStatus current = of(order.getStatus());
        if (current == null) {
            throw new BusinessException("订单状态异常: " + order.getStatus());
        }
        if (current == target) {
            // 同状态视为幂等，直接返回
            return;
        }
        if (!current.canTransitTo(target)) {
            throw new BusinessException("订单当前为[" + current.desc + "]，不允许变更为[" + target.desc + "]");
        }
        order.setStatus(target.getCode());
    }
}

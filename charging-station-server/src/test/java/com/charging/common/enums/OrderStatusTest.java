package com.charging.common.enums;

import com.charging.common.exception.BusinessException;
import com.charging.entity.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单状态机单元测试（纯内存，不依赖 Spring/DB/Redis，可在 CI 中直接跑）
 */
class OrderStatusTest {

    private Order orderWith(Integer status) {
        Order order = new Order();
        order.setStatus(status);
        return order;
    }

    @Test
    @DisplayName("合法流转：充电中 -> 已完成/待支付/已取消")
    void shouldAllowLegalTransitionsFromCharging() {
        assertAll(
                () -> {
                    Order order = orderWith(1);
                    OrderStatus.transit(order, OrderStatus.COMPLETED);
                    assertEquals(3, order.getStatus());
                },
                () -> {
                    Order order = orderWith(1);
                    OrderStatus.transit(order, OrderStatus.UNPAID);
                    assertEquals(2, order.getStatus());
                },
                () -> {
                    Order order = orderWith(1);
                    OrderStatus.transit(order, OrderStatus.CANCELLED);
                    assertEquals(0, order.getStatus());
                }
        );
    }

    @Test
    @DisplayName("合法流转：待支付 -> 已完成")
    void shouldAllowUnpaidToCompleted() {
        Order order = orderWith(2);
        OrderStatus.transit(order, OrderStatus.COMPLETED);
        assertEquals(3, order.getStatus());
    }

    @Test
    @DisplayName("终态不可回退：已完成/已取消 不允许再流转")
    void shouldRejectTransitionsFromFinalStates() {
        assertAll(
                () -> assertThrows(BusinessException.class,
                        () -> OrderStatus.transit(orderWith(3), OrderStatus.CHARGING)),
                () -> assertThrows(BusinessException.class,
                        () -> OrderStatus.transit(orderWith(0), OrderStatus.CHARGING)),
                () -> assertThrows(BusinessException.class,
                        () -> OrderStatus.transit(orderWith(3), OrderStatus.UNPAID))
        );
    }

    @Test
    @DisplayName("非法跳转：充电中不能直接回到充电中以外的初始态被误判，待支付不能回到充电中")
    void shouldRejectIllegalBackwardTransition() {
        assertThrows(BusinessException.class,
                () -> OrderStatus.transit(orderWith(2), OrderStatus.CHARGING));
    }

    @Test
    @DisplayName("同状态流转视为幂等，不抛异常")
    void shouldBeIdempotentOnSameStatus() {
        Order order = orderWith(1);
        OrderStatus.transit(order, OrderStatus.CHARGING);
        assertEquals(1, order.getStatus());
    }

    @Test
    @DisplayName("未知状态码应拒绝流转")
    void shouldRejectUnknownStatus() {
        assertThrows(BusinessException.class,
                () -> OrderStatus.transit(orderWith(99), OrderStatus.COMPLETED));
    }

    @Test
    @DisplayName("canTransitTo 与 transit 判定一致")
    void canTransitToShouldMatchTransit() {
        assertAll(
                () -> assertTrue(OrderStatus.of(1).canTransitTo(OrderStatus.COMPLETED)),
                () -> assertFalse(OrderStatus.of(3).canTransitTo(OrderStatus.CHARGING)),
                () -> assertFalse(OrderStatus.of(0).canTransitTo(OrderStatus.UNPAID)),
                () -> assertFalse(OrderStatus.of(1).canTransitTo(null))
        );
    }
}

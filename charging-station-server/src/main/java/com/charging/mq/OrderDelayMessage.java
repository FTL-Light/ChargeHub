package com.charging.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单延迟消息体
 *
 * <p>只包含 orderId 与类型：<b>不要使用消息体携带业务快照</b>。
 * 消费端可能延迟 12 小时才处理，那时的金额/时长早已变化，必须以数据库当前状态为准，
 * 消息只作为"该处理这个订单了"的信号。这也是幂等能成立的前提。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDelayMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单ID */
    private Long orderId;

    /** 订单号，便于日志与人工排查 */
    private String orderNo;

    /**
     * 消息类型，取值见 {@link MqConstants#TYPE_CHARGING_TIMEOUT} / {@link MqConstants#TYPE_UNPAID_CLOSE}
     */
    private String type;

    /** 消息投递时间（用于观测实际延迟是否准确） */
    private LocalDateTime publishTime;
}

package com.charging.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 停止充电请求
 */
@Data
@Schema(description = "停止充电请求")
public class StopChargingDTO {

    @Schema(description = "订单ID")
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @Schema(description = "支付方式: 1-余额, 2-微信")
    private Integer payType;
}

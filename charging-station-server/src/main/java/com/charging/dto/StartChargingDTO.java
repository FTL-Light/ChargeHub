package com.charging.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 开始充电请求
 */
@Data
@Schema(description = "开始充电请求")
public class StartChargingDTO {

    @Schema(description = "站点ID")
    @NotNull(message = "站点ID不能为空")
    private Long stationId;

    @Schema(description = "充电桩ID")
    @NotNull(message = "充电桩ID不能为空")
    private Long chargerId;

    @Schema(description = "充电枪ID")
    @NotNull(message = "充电枪ID不能为空")
    private Long gunId;

    @Schema(description = "支付方式: 1-余额, 2-微信")
    private Integer payType;
}

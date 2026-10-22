package com.charging.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 开始充电请求
 * 扫码场景只需传 chargerId：站点由充电桩反查，充电枪自动选取该桩下第一把空闲枪。
 */
@Data
@Schema(description = "开始充电请求")
public class StartChargingDTO {

    @Schema(description = "站点ID(可选，服务端以充电桩归属站点为准)")
    private Long stationId;

    @Schema(description = "充电桩ID")
    @NotNull(message = "充电桩ID不能为空")
    private Long chargerId;

    @Schema(description = "充电枪ID(可选，不传则自动选择该桩下第一把空闲枪)")
    private Long gunId;

    @Schema(description = "支付方式: 1-余额, 2-微信")
    private Integer payType;
}

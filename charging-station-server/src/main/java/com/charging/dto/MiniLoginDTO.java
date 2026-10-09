package com.charging.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 小程序用户登录请求
 */
@Data
@Schema(description = "小程序用户登录请求")
public class MiniLoginDTO {

    @Schema(description = "微信code")
    @NotBlank(message = "code不能为空")
    private String code;

    @Schema(description = "微信昵称")
    private String nickname;

    @Schema(description = "微信头像")
    private String avatar;
}

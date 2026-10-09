package com.charging.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 对话请求
 */
@Data
@Schema(description = "AI对话请求")
public class AiChatDTO {

    @Schema(description = "会话ID(为空则新建会话)")
    private String sessionId;

    @Schema(description = "用户消息")
    @NotBlank(message = "消息内容不能为空")
    private String message;
}

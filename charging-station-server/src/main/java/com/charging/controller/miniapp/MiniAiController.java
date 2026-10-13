package com.charging.controller.miniapp;

import cn.hutool.core.util.IdUtil;
import com.charging.common.exception.BusinessException;
import com.charging.common.result.R;
import com.charging.dto.AiChatDTO;
import com.charging.entity.AiChatLog;
import com.charging.service.AiChatLogService;
import com.charging.utils.AiRateLimiter;
import com.charging.utils.AiUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 小程序端-AI客服控制器
 */
@Slf4j
@RestController
@RequestMapping("/miniapp/ai")
@RequiredArgsConstructor
@Tag(name = "小程序端-AI客服", description = "AI客服对话相关接口")
public class MiniAiController {

    private final AiUtil aiUtil;
    private final AiChatLogService aiChatLogService;
    private final AiRateLimiter aiRateLimiter;

    @PostMapping("/chat")
    @Operation(summary = "AI对话")
    public R<Map<String, Object>> chat(@Valid @RequestBody AiChatDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        // 每日调用次数限制，防止接口被刷产生高额 token 费用
        if (!aiRateLimiter.tryAcquire(userId)) {
            throw new BusinessException("今日AI咨询次数已达上限，请明天再来");
        }

        // 生成或使用已有会话ID
        String sessionId = dto.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = IdUtil.simpleUUID();
        }

        // 获取最近对话历史(最多10条)作为上下文
        List<AiChatLog> history = aiChatLogService.getRecentHistory(userId, sessionId, 10);

        // 构建消息列表
        List<Map<String, String>> messages = new ArrayList<>();
        // 按时间正序加入历史消息
        for (int i = history.size() - 1; i >= 0; i--) {
            AiChatLog logItem = history.get(i);
            Map<String, String> msg = new HashMap<>();
            msg.put("role", logItem.getRole());
            msg.put("content", logItem.getContent());
            messages.add(msg);
        }
        // 加入当前用户消息
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", dto.getMessage());
        messages.add(userMsg);

        // 保存用户消息
        AiChatLog userLog = new AiChatLog();
        userLog.setUserId(userId);
        userLog.setSessionId(sessionId);
        userLog.setRole("user");
        userLog.setContent(dto.getMessage());
        aiChatLogService.save(userLog);

        // 调用 AI 获取回复
        String reply = aiUtil.chat(messages);

        // 保存 AI 回复
        AiChatLog assistantLog = new AiChatLog();
        assistantLog.setUserId(userId);
        assistantLog.setSessionId(sessionId);
        assistantLog.setRole("assistant");
        assistantLog.setContent(reply);
        aiChatLogService.save(assistantLog);

        // 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("reply", reply);
        return R.ok(result);
    }

    @GetMapping("/history")
    @Operation(summary = "获取对话历史")
    public R<List<AiChatLog>> history(
            @Parameter(description = "会话ID") @RequestParam(required = false) String sessionId,
            @Parameter(description = "限制条数") @RequestParam(defaultValue = "50") Integer limit,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<AiChatLog> history = aiChatLogService.getRecentHistory(userId, sessionId, limit);
        return R.ok(history);
    }
}

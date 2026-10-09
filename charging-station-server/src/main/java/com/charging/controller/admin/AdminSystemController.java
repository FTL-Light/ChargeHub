package com.charging.controller.admin;

import com.charging.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理端-系统管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/system")
@RequiredArgsConstructor
@Tag(name = "管理端-系统管理", description = "系统配置相关接口")
public class AdminSystemController {

    @Value("${ai.provider:deepseek}")
    private String aiProvider;

    @Value("${ai.api-key:}")
    private String aiApiKey;

    @Value("${ai.api-url:https://api.deepseek.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${ai.model:deepseek-chat}")
    private String aiModel;

    @Value("${ai.system-prompt:}")
    private String aiSystemPrompt;

    @GetMapping("/ai-config")
    @Operation(summary = "获取AI客服配置")
    public R<Map<String, Object>> getAiConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("provider", aiProvider);
        // 脱敏显示API Key
        config.put("apiKey", aiApiKey == null || aiApiKey.isEmpty() ? "" :
                aiApiKey.substring(0, Math.min(8, aiApiKey.length())) + "****");
        config.put("apiUrl", apiUrl);
        config.put("model", aiModel);
        config.put("systemPrompt", aiSystemPrompt);
        return R.ok(config);
    }

    @PutMapping("/ai-config")
    @Operation(summary = "更新AI客服配置")
    public R<Void> updateAiConfig(@RequestBody Map<String, Object> config) {
        // 注意：运行时修改配置需要重启服务才能生效
        // 实际项目中可将配置存储到数据库或配置中心
        log.info("更新AI客服配置请求: provider={}, model={}", config.get("provider"), config.get("model"));
        return R.ok();
    }
}

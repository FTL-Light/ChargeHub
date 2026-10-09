package com.charging.controller.miniapp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.charging.common.result.R;
import com.charging.entity.Feedback;
import com.charging.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 小程序端-反馈控制器
 */
@Slf4j
@RestController
@RequestMapping("/miniapp/feedback")
@RequiredArgsConstructor
@Tag(name = "小程序端-反馈", description = "反馈相关接口")
public class MiniFeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    @Operation(summary = "提交反馈")
    public R<Void> submit(@RequestBody Feedback feedback, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        feedback.setId(null);
        feedback.setUserId(userId);
        feedback.setStatus(0); // 待处理
        feedbackService.save(feedback);
        return R.ok("提交成功");
    }

    @GetMapping("/list")
    @Operation(summary = "我的反馈列表")
    public R<Page<Feedback>> list(
            @Parameter(description = "当前页") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Page<Feedback> page = new Page<>(current, size);
        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Feedback::getUserId, userId);
        wrapper.orderByDesc(Feedback::getCreateTime);
        return R.ok(feedbackService.page(page, wrapper));
    }
}

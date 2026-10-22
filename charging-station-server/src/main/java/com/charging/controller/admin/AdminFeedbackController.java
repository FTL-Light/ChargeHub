package com.charging.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.charging.common.PageUtil;
import com.charging.common.exception.BusinessException;
import com.charging.common.result.R;
import com.charging.entity.Feedback;
import com.charging.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 管理端-反馈控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/feedback")
@RequiredArgsConstructor
@Tag(name = "管理端-反馈管理", description = "反馈相关接口")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    @GetMapping("/list")
    @Operation(summary = "分页查询反馈列表")
    public R<Page<Feedback>> list(
            @Parameter(description = "当前页") @RequestParam(required = false) Integer current,
            @Parameter(description = "页码(兼容前端 page 参数)") @RequestParam(required = false) Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "反馈类型") @RequestParam(required = false) Integer type,
            @Parameter(description = "状态") @RequestParam(required = false) Integer status) {
        Page<Feedback> pageResult = PageUtil.of(current, page, size);
        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(type != null, Feedback::getType, type);
        wrapper.eq(status != null, Feedback::getStatus, status);
        wrapper.orderByDesc(Feedback::getCreateTime);
        return R.ok(feedbackService.page(pageResult, wrapper));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取反馈详情")
    public R<Feedback> detail(@Parameter(description = "反馈ID") @PathVariable Long id) {
        Feedback feedback = feedbackService.getById(id);
        if (feedback == null) {
            throw new BusinessException("反馈不存在");
        }
        return R.ok(feedback);
    }

    @PutMapping("/handle")
    @Operation(summary = "处理反馈")
    public R<Void> handle(@RequestBody Feedback feedback) {
        if (feedback.getId() == null) {
            throw new BusinessException("反馈ID不能为空");
        }
        Feedback update = new Feedback();
        update.setId(feedback.getId());
        update.setStatus(1);
        update.setReply(feedback.getReply());
        update.setHandleTime(LocalDateTime.now());
        feedbackService.updateById(update);
        return R.ok();
    }
}

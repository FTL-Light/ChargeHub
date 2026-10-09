package com.charging.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.charging.common.result.R;
import com.charging.entity.LoginLog;
import com.charging.service.LoginLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端-登录日志控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/login-log")
@RequiredArgsConstructor
@Tag(name = "管理端-登录日志", description = "登录日志相关接口")
public class AdminLoginLogController {

    private final LoginLogService loginLogService;

    @GetMapping("/list")
    @Operation(summary = "分页查询登录日志")
    public R<Page<LoginLog>> list(
            @Parameter(description = "当前页") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "用户名") @RequestParam(required = false) String username,
            @Parameter(description = "用户类型") @RequestParam(required = false) Integer userType,
            @Parameter(description = "状态") @RequestParam(required = false) Integer status) {
        Page<LoginLog> page = new Page<>(current, size);
        LambdaQueryWrapper<LoginLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(username != null && !username.isEmpty(), LoginLog::getUsername, username);
        wrapper.eq(userType != null, LoginLog::getUserType, userType);
        wrapper.eq(status != null, LoginLog::getStatus, status);
        wrapper.orderByDesc(LoginLog::getLoginTime);
        return R.ok(loginLogService.page(page, wrapper));
    }
}

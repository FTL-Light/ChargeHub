package com.charging.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.charging.common.PageUtil;
import com.charging.common.result.R;
import com.charging.entity.User;
import com.charging.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端-用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
@Tag(name = "管理端-用户管理", description = "用户相关接口")
public class AdminUserController {

    private final UserService userService;

    @GetMapping("/list")
    @Operation(summary = "分页查询用户列表")
    public R<Page<User>> list(
            @Parameter(description = "当前页") @RequestParam(required = false) Integer current,
            @Parameter(description = "页码(兼容前端 page 参数)") @RequestParam(required = false) Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "用户名") @RequestParam(required = false) String username,
            @Parameter(description = "手机号") @RequestParam(required = false) String phone,
            @Parameter(description = "状态") @RequestParam(required = false) Integer status) {
        Page<User> pageResult = PageUtil.of(current, page, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(username != null && !username.isEmpty(), User::getUsername, username);
        wrapper.like(phone != null && !phone.isEmpty(), User::getPhone, phone);
        wrapper.eq(status != null, User::getStatus, status);
        wrapper.orderByDesc(User::getCreateTime);
        Page<User> result = userService.page(pageResult, wrapper);
        // 清除密码
        result.getRecords().forEach(u -> u.setPassword(null));
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取用户详情")
    public R<User> detail(@Parameter(description = "用户ID") @PathVariable Long id) {
        User user = userService.getById(id);
        if (user != null) {
            user.setPassword(null);
        }
        return R.ok(user);
    }

    @PutMapping
    @Operation(summary = "更新用户状态")
    public R<Void> update(@RequestBody User user) {
        User update = new User();
        update.setId(user.getId());
        update.setStatus(user.getStatus());
        userService.updateById(update);
        return R.ok();
    }
}

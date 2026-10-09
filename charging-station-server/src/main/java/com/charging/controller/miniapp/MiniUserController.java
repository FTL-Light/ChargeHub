package com.charging.controller.miniapp;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.charging.common.exception.BusinessException;
import com.charging.common.result.R;
import com.charging.dto.MiniLoginDTO;
import com.charging.dto.MiniRegisterDTO;
import com.charging.entity.User;
import com.charging.service.UserService;
import com.charging.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 小程序端-用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/miniapp/user")
@RequiredArgsConstructor
@Tag(name = "小程序端-用户", description = "用户相关接口")
public class MiniUserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/login")
    @Operation(summary = "微信登录")
    public R<Map<String, Object>> login(@Valid @RequestBody MiniLoginDTO dto) {
        // 模拟微信登录: 通过 code 生成 openid
        // 实际项目中应调用微信接口 code2session 获取 openid
        String openid = SecureUtil.md5(dto.getCode());

        // 根据 openid 查询用户
        User user = userService.lambdaQuery()
                .eq(User::getOpenid, openid)
                .one();

        if (user == null) {
            // 新用户自动注册
            user = new User();
            user.setOpenid(openid);
            user.setUsername("wx_" + IdUtil.simpleUUID().substring(0, 8));
            user.setNickname(dto.getNickname() != null ? dto.getNickname() : "微信用户");
            user.setAvatar(dto.getAvatar());
            user.setBalance(BigDecimal.ZERO);
            user.setStatus(1);
            userService.save(user);
        } else {
            // 更新昵称头像
            boolean needUpdate = false;
            if (dto.getNickname() != null && !dto.getNickname().isEmpty()) {
                user.setNickname(dto.getNickname());
                needUpdate = true;
            }
            if (dto.getAvatar() != null && !dto.getAvatar().isEmpty()) {
                user.setAvatar(dto.getAvatar());
                needUpdate = true;
            }
            if (needUpdate) {
                userService.updateById(user);
            }
        }

        // 检查状态
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        // 生成 token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), 2);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        user.setPassword(null);
        result.put("userInfo", user);
        return R.ok("登录成功", result);
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public R<Map<String, Object>> register(@Valid @RequestBody MiniRegisterDTO dto) {
        // 检查用户名是否已存在
        long count = userService.lambdaQuery()
                .eq(User::getUsername, dto.getUsername())
                .count();
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }

        // 创建用户
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setPhone(dto.getPhone());
        user.setNickname(dto.getNickname() != null ? dto.getNickname() : dto.getUsername());
        user.setBalance(BigDecimal.ZERO);
        user.setStatus(1);
        userService.save(user);

        // 生成 token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), 2);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        user.setPassword(null);
        result.put("userInfo", user);
        return R.ok("注册成功", result);
    }

    @GetMapping("/info")
    @Operation(summary = "获取当前用户信息")
    public R<User> info(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(null);
        return R.ok(user);
    }

    @GetMapping("/balance")
    @Operation(summary = "获取用户余额")
    public R<Map<String, Object>> balance(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("balance", user.getBalance() == null ? BigDecimal.ZERO : user.getBalance());
        return R.ok(result);
    }
}

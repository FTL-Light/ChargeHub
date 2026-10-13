package com.charging.controller.admin;

import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.charging.common.exception.BusinessException;
import com.charging.common.result.R;
import com.charging.dto.AdminLoginDTO;
import com.charging.entity.Admin;
import com.charging.entity.LoginLog;
import com.charging.service.AdminService;
import com.charging.service.LoginLogService;
import com.charging.utils.JwtUtil;
import com.charging.utils.LoginAttemptLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理端登录控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "管理端-登录", description = "管理员登录相关接口")
public class AdminLoginController {

    private final AdminService adminService;
    private final LoginLogService loginLogService;
    private final JwtUtil jwtUtil;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/login")
    @Operation(summary = "管理员登录")
    public R<Map<String, Object>> login(@Valid @RequestBody AdminLoginDTO dto, HttpServletRequest request) {
        // 根据用户名查询管理员
        Admin admin = adminService.lambdaQuery()
                .eq(Admin::getUsername, dto.getUsername())
                .one();

        String clientIp = getClientIp(request);
        String limiterKey = dto.getUsername() + "@" + clientIp;

        // 记录登录日志
        LoginLog loginLog = new LoginLog();
        loginLog.setUserType(1);
        loginLog.setUsername(dto.getUsername());
        loginLog.setLoginIp(clientIp);
        loginLog.setLoginTime(LocalDateTime.now());

        // 解析浏览器和操作系统
        String userAgentStr = request.getHeader("User-Agent");
        UserAgent userAgent = UserAgentUtil.parse(userAgentStr);
        if (userAgent != null) {
            loginLog.setBrowser(userAgent.getBrowser() != null ? userAgent.getBrowser().getName() : "unknown");
            loginLog.setOs(userAgent.getOs() != null ? userAgent.getOs().getName() : "unknown");
        }
        // 根据IP获取登录地点(简化处理)
        loginLog.setLoginLocation("未知");

        // 登录失败限流检查(防暴力破解)，锁定期间同样记录日志
        if (loginAttemptLimiter.isBlocked(limiterKey)) {
            loginLog.setStatus(0);
            loginLog.setMsg("失败次数过多，临时锁定");
            loginLogService.save(loginLog);
            throw new BusinessException("登录失败次数过多，请15分钟后再试");
        }

        if (admin == null) {
            loginAttemptLimiter.recordFailure(limiterKey);
            loginLog.setStatus(0);
            loginLog.setMsg("用户不存在");
            loginLogService.save(loginLog);
            throw new BusinessException("用户名或密码错误");
        }

        // 验证密码 (BCrypt)
        if (!passwordEncoder.matches(dto.getPassword(), admin.getPassword())) {
            loginAttemptLimiter.recordFailure(limiterKey);
            loginLog.setUserId(admin.getId());
            loginLog.setStatus(0);
            loginLog.setMsg("密码错误");
            loginLogService.save(loginLog);
            throw new BusinessException("用户名或密码错误");
        }

        // 检查状态
        if (admin.getStatus() != null && admin.getStatus() == 0) {
            loginLog.setUserId(admin.getId());
            loginLog.setStatus(0);
            loginLog.setMsg("账号已禁用");
            loginLogService.save(loginLog);
            throw new BusinessException("账号已被禁用，请联系超级管理员");
        }

        // 生成 token (登录成功，清除该账号的失败计数)
        loginAttemptLimiter.clear(limiterKey);
        String token = jwtUtil.generateToken(admin.getId(), admin.getUsername(), 1);

        // 记录登录成功日志
        loginLog.setUserId(admin.getId());
        loginLog.setStatus(1);
        loginLog.setMsg("登录成功");
        loginLogService.save(loginLog);

        // 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        // 清除密码
        admin.setPassword(null);
        result.put("admin", admin);

        return R.ok("登录成功", result);
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}

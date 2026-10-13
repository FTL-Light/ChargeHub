package com.charging.interceptor;

import com.charging.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理端角色拦截器
 * 校验 JWT 中的 userType 是否为管理员(1)，防止小程序用户越权访问管理端接口。
 * 需注册在 JwtInterceptor 之后，依赖其写入的 userType 属性。
 */
public class AdminAuthInterceptor implements HandlerInterceptor {

    /**
     * 管理员用户类型
     */
    private static final Integer USER_TYPE_ADMIN = 1;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Object userType = request.getAttribute("userType");
        if (!USER_TYPE_ADMIN.equals(userType)) {
            throw new BusinessException(403, "无权限访问");
        }
        return true;
    }
}

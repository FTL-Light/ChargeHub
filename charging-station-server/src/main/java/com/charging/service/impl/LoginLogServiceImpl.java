package com.charging.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.charging.entity.LoginLog;
import com.charging.mapper.LoginLogMapper;
import com.charging.service.LoginLogService;
import org.springframework.stereotype.Service;

/**
 * 登录日志服务实现类
 */
@Service
public class LoginLogServiceImpl extends ServiceImpl<LoginLogMapper, LoginLog> implements LoginLogService {
}

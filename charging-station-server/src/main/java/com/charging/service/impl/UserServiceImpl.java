package com.charging.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.charging.entity.User;
import com.charging.mapper.UserMapper;
import com.charging.service.UserService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 用户服务实现类
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public boolean addBalance(Long userId, BigDecimal amount) {
        return baseMapper.addBalance(userId, amount) > 0;
    }

    @Override
    public boolean deductBalance(Long userId, BigDecimal amount) {
        return baseMapper.deductBalance(userId, amount) > 0;
    }
}

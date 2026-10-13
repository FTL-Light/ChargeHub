package com.charging.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.charging.entity.User;

import java.math.BigDecimal;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {

    /**
     * 原子增加余额
     *
     * @param userId 用户ID
     * @param amount 金额
     * @return 是否成功
     */
    boolean addBalance(Long userId, BigDecimal amount);

    /**
     * 原子扣减余额(余额不足时失败)
     *
     * @param userId 用户ID
     * @param amount 金额
     * @return 是否成功
     */
    boolean deductBalance(Long userId, BigDecimal amount);
}

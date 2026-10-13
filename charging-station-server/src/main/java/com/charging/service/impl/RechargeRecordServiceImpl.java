package com.charging.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.charging.common.exception.BusinessException;
import com.charging.entity.RechargeRecord;
import com.charging.entity.User;
import com.charging.mapper.RechargeRecordMapper;
import com.charging.service.RechargeRecordService;
import com.charging.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 充值记录服务实现类
 */
@Service
@RequiredArgsConstructor
public class RechargeRecordServiceImpl extends ServiceImpl<RechargeRecordMapper, RechargeRecord> implements RechargeRecordService {

    private final UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RechargeRecord recharge(Long userId, BigDecimal amount, Integer payType, String requestNo) {
        // 幂等校验：同一请求号直接返回已有记录，防止网络重试导致重复充值
        if (requestNo != null && !requestNo.isEmpty()) {
            RechargeRecord existing = this.lambdaQuery()
                    .eq(RechargeRecord::getRequestNo, requestNo)
                    .one();
            if (existing != null) {
                return existing;
            }
        }

        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 创建充值记录
        RechargeRecord record = new RechargeRecord();
        record.setUserId(userId);
        record.setAmount(amount);
        record.setPayType(payType != null ? payType : 1);
        record.setRequestNo(requestNo);
        record.setStatus(1); // 成功(模拟支付成功)
        try {
            this.save(record);
        } catch (DuplicateKeyException e) {
            // 并发重复提交命中唯一索引，返回已存在的记录
            if (requestNo != null && !requestNo.isEmpty()) {
                RechargeRecord existing = this.lambdaQuery()
                        .eq(RechargeRecord::getRequestNo, requestNo)
                        .one();
                if (existing != null) {
                    return existing;
                }
            }
            throw new BusinessException("充值重复提交，请稍后再试");
        }

        // 原子增加余额(数据库层面自增，避免并发丢失更新)
        boolean updated = userService.addBalance(userId, amount);
        if (!updated) {
            throw new BusinessException("充值失败，请稍后再试");
        }
        return record;
    }
}

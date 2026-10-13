package com.charging.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.charging.entity.RechargeRecord;

import java.math.BigDecimal;

/**
 * 充值记录服务接口
 */
public interface RechargeRecordService extends IService<RechargeRecord> {

    /**
     * 账户充值(写充值记录与增加余额在同一事务内)
     *
     * @param userId    用户ID
     * @param amount    充值金额
     * @param payType   支付方式
     * @param requestNo 幂等请求号(可为空)
     * @return 充值记录
     */
    RechargeRecord recharge(Long userId, BigDecimal amount, Integer payType, String requestNo);
}

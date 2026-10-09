package com.charging.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.charging.entity.RechargeRecord;
import com.charging.mapper.RechargeRecordMapper;
import com.charging.service.RechargeRecordService;
import org.springframework.stereotype.Service;

/**
 * 充值记录服务实现类
 */
@Service
public class RechargeRecordServiceImpl extends ServiceImpl<RechargeRecordMapper, RechargeRecord> implements RechargeRecordService {
}

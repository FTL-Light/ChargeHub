package com.charging.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.charging.entity.RechargeRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 充值记录 Mapper
 */
@Mapper
public interface RechargeRecordMapper extends BaseMapper<RechargeRecord> {
}

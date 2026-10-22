package com.charging.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.charging.entity.RechargeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值记录 Mapper
 */
@Mapper
public interface RechargeRecordMapper extends BaseMapper<RechargeRecord> {

    /**
     * 统计用户在指定时间点之后成功充值的累计金额(用于单日限额校验)
     *
     * @param userId 用户ID
     * @param start  统计起始时间
     * @return 累计金额，无记录返回 0
     */
    @Select("SELECT IFNULL(SUM(amount), 0) FROM t_recharge_record "
            + "WHERE user_id = #{userId} AND status = 1 AND deleted = 0 AND create_time >= #{start}")
    BigDecimal sumSuccessAmountSince(@Param("userId") Long userId, @Param("start") LocalDateTime start);
}

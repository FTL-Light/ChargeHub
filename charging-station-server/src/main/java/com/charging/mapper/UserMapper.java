package com.charging.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.charging.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 用户 Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 原子增加余额(数据库层面自增，避免并发丢失更新)
     *
     * @param userId 用户ID
     * @param amount 金额
     * @return 影响行数
     */
    @Update("UPDATE t_user SET balance = COALESCE(balance, 0) + #{amount} WHERE id = #{userId} AND deleted = 0")
    int addBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 原子扣减余额(仅当余额充足时扣减成功)
     *
     * @param userId 用户ID
     * @param amount 金额
     * @return 影响行数，0 表示余额不足或用户不存在
     */
    @Update("UPDATE t_user SET balance = COALESCE(balance, 0) - #{amount} "
            + "WHERE id = #{userId} AND COALESCE(balance, 0) >= #{amount} AND deleted = 0")
    int deductBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}

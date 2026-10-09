package com.charging.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.charging.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 订单 Mapper
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 统计近7天订单数量及收入
     *
     * @return 每日统计列表
     */
    @Select("SELECT DATE(create_time) as date, COUNT(*) as orderCount, IFNULL(SUM(pay_amount), 0) as income " +
            "FROM t_order " +
            "WHERE deleted = 0 AND create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) " +
            "GROUP BY DATE(create_time) " +
            "ORDER BY date ASC")
    List<Map<String, Object>> countLast7Days();

    /**
     * 按充电类型统计订单数量
     *
     * @return 充电类型分布
     */
    @Select("SELECT c.type as type, COUNT(o.id) as count " +
            "FROM t_order o " +
            "LEFT JOIN t_charger c ON o.charger_id = c.id AND c.deleted = 0 " +
            "WHERE o.deleted = 0 " +
            "GROUP BY c.type")
    List<Map<String, Object>> countByChargerType();

    /**
     * 统计总收入
     *
     * @return 总收入
     */
    @Select("SELECT IFNULL(SUM(pay_amount), 0) FROM t_order WHERE deleted = 0 AND status = 3")
    BigDecimal sumTotalIncome();
}

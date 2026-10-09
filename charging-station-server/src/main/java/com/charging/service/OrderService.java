package com.charging.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.charging.entity.Order;

import java.util.Map;

/**
 * 订单服务接口
 */
public interface OrderService extends IService<Order> {

    /**
     * 获取统计数据
     * 返回总订单数、总用户数、站点数量、总收入、近7天趋势数据、充电类型分布
     *
     * @return 统计数据
     */
    Map<String, Object> getStatistics();
}

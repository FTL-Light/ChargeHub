package com.charging.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.charging.common.CacheKeys;
import com.charging.entity.Order;
import com.charging.mapper.OrderMapper;
import com.charging.service.OrderService;
import com.charging.service.StationService;
import com.charging.service.UserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 订单服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private final UserService userService;
    private final StationService stationService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> getStatistics() {
        try {
            String cached = stringRedisTemplate.opsForValue().get(CacheKeys.ORDER_STATS);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            log.warn("Redis 订单统计缓存读取失败, 降级直查数据库", e);
        }

        Map<String, Object> result = doGetStatistics();
        try {
            stringRedisTemplate.opsForValue().set(CacheKeys.ORDER_STATS,
                    objectMapper.writeValueAsString(result), 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis 订单统计缓存写入失败", e);
        }
        return result;
    }

    /**
     * 实际统计计算（聚合查询开销大，上层以 5 分钟缓存兜底）
     */
    private Map<String, Object> doGetStatistics() {
        Map<String, Object> result = new HashMap<>();

        // 总订单数
        long totalOrders = this.count();
        result.put("totalOrders", totalOrders);

        // 总用户数
        long totalUsers = userService.count();
        result.put("totalUsers", totalUsers);

        // 站点数量
        long totalStations = stationService.count();
        result.put("totalStations", totalStations);

        // 总收入
        BigDecimal totalIncome = baseMapper.sumTotalIncome();
        result.put("totalIncome", totalIncome == null ? BigDecimal.ZERO : totalIncome);

        // 近7天趋势数据
        List<Map<String, Object>> last7DaysData = baseMapper.countLast7Days();
        // 构建完整7天数据(含无数据日期)
        List<Map<String, Object>> trendList = buildLast7DaysTrend(last7DaysData);
        result.put("trend", trendList);

        // 充电类型分布
        List<Map<String, Object>> chargerTypeDistribution = baseMapper.countByChargerType();
        result.put("chargerTypeDistribution", chargerTypeDistribution);

        return result;
    }

    /**
     * 构建完整近7天趋势数据(补齐无数据日期)
     */
    private List<Map<String, Object>> buildLast7DaysTrend(List<Map<String, Object>> rawData) {
        Map<String, Map<String, Object>> dateMap = new LinkedHashMap<>();
        // 初始化最近7天
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String dateStr = date.toString();
            Map<String, Object> item = new HashMap<>();
            item.put("date", dateStr);
            item.put("orderCount", 0);
            item.put("income", BigDecimal.ZERO);
            dateMap.put(dateStr, item);
        }
        // 填充查询到的数据
        if (rawData != null) {
            for (Map<String, Object> row : rawData) {
                Object dateObj = row.get("date");
                if (dateObj == null) {
                    continue;
                }
                String dateStr = dateObj.toString();
                if (dateMap.containsKey(dateStr)) {
                    Map<String, Object> item = dateMap.get(dateStr);
                    item.put("orderCount", row.get("orderCount"));
                    Object income = row.get("income");
                    item.put("income", income == null ? BigDecimal.ZERO : income);
                }
            }
        }
        return new ArrayList<>(dateMap.values());
    }
}

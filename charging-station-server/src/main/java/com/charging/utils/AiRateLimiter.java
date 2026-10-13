package com.charging.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 对话限流器(内存版每日配额)
 * 限制每用户每日调用次数，防止 AI 接口被刷产生高额 token 费用。
 * 仅适用于单实例部署，多实例部署需改用 Redis 等集中式存储。
 */
@Component
public class AiRateLimiter {

    /**
     * 每用户每日调用上限
     */
    @Value("${ai.daily-limit:50}")
    private int dailyLimit;

    private final Map<Long, DayCounter> counters = new ConcurrentHashMap<>();

    /**
     * 尝试获取一次调用配额
     *
     * @param userId 用户ID
     * @return 是否允许调用
     */
    public boolean tryAcquire(Long userId) {
        if (dailyLimit <= 0) {
            return true;
        }
        String today = LocalDate.now().toString();
        DayCounter counter = counters.computeIfAbsent(userId, k -> new DayCounter());
        synchronized (counter) {
            if (!today.equals(counter.day)) {
                counter.day = today;
                counter.count = 0;
            }
            if (counter.count >= dailyLimit) {
                return false;
            }
            counter.count++;
            return true;
        }
    }

    /**
     * 按日计数的配额
     */
    private static class DayCounter {
        private String day;
        private int count;
    }
}

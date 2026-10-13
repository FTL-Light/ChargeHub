package com.charging.utils;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流器(内存版滑动窗口)
 * 同一账号+IP 在窗口期内累计失败达到上限后临时锁定，防止暴力破解。
 * 仅适用于单实例部署，多实例部署需改用 Redis 等集中式存储。
 */
@Component
public class LoginAttemptLimiter {

    /**
     * 窗口期内允许的最大失败次数
     */
    private static final int MAX_ATTEMPTS = 5;

    /**
     * 窗口时长(毫秒): 15分钟
     */
    private static final long WINDOW_MILLIS = 15 * 60 * 1000L;

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    /**
     * 是否已被临时锁定
     *
     * @param key 限流键(用户名+IP)
     * @return 是否锁定
     */
    public boolean isBlocked(String key) {
        Deque<Long> deque = attempts.get(key);
        if (deque == null) {
            return false;
        }
        evictExpired(deque, System.currentTimeMillis());
        return deque.size() >= MAX_ATTEMPTS;
    }

    /**
     * 记录一次失败
     *
     * @param key 限流键(用户名+IP)
     */
    public void recordFailure(String key) {
        Deque<Long> deque = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        synchronized (deque) {
            deque.addLast(now);
            evictExpired(deque, now);
        }
    }

    /**
     * 登录成功后清除失败记录
     *
     * @param key 限流键(用户名+IP)
     */
    public void clear(String key) {
        attempts.remove(key);
    }

    /**
     * 清理窗口外的过期记录
     */
    private void evictExpired(Deque<Long> deque, long now) {
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() > WINDOW_MILLIS) {
                deque.pollFirst();
            }
        }
    }
}

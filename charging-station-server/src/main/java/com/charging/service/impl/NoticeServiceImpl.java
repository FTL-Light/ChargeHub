package com.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.charging.common.CacheKeys;
import com.charging.entity.Notice;
import com.charging.mapper.NoticeMapper;
import com.charging.service.NoticeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 公告服务实现类
 * 公告列表仅缓存无类型筛选的前3页热点数据；增删改时联动删除缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeServiceImpl extends ServiceImpl<NoticeMapper, Notice> implements NoticeService {

    private static final int MAX_CACHED_PAGE = 3;
    private static final long CACHE_MINUTES = 10L;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Page<Notice> pageCached(int current, int size, Integer type) {
        boolean cacheable = type == null && current >= 1 && current <= MAX_CACHED_PAGE;
        String key = cacheable ? CacheKeys.noticeList(current) : null;

        if (cacheable) {
            try {
                String cached = stringRedisTemplate.opsForValue().get(key);
                if (cached != null) {
                    return objectMapper.readValue(cached, new TypeReference<Page<Notice>>() {});
                }
            } catch (Exception e) {
                log.warn("Redis 公告列表缓存读取失败, key={}, 降级直查数据库", key, e);
            }
        }

        Page<Notice> page = new Page<>(current, size);
        LambdaQueryWrapper<Notice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notice::getStatus, 1);
        wrapper.eq(type != null, Notice::getType, type);
        wrapper.orderByDesc(Notice::getTop).orderByDesc(Notice::getPublishTime);
        Page<Notice> result = this.page(page, wrapper);

        if (cacheable) {
            try {
                // TTL 加 0~60 秒随机抖动，避免同批 key 同时过期形成缓存雪崩
                long expireSeconds = CACHE_MINUTES * 60 + ThreadLocalRandom.current().nextInt(60);
                stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result),
                        expireSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Redis 公告列表缓存写入失败, key={}", key, e);
            }
        }
        return result;
    }

    @Override
    public boolean save(Notice notice) {
        boolean result = super.save(notice);
        if (result) {
            evictListCache();
        }
        return result;
    }

    @Override
    public boolean updateById(Notice notice) {
        boolean result = super.updateById(notice);
        if (result) {
            evictListCache();
        }
        return result;
    }

    @Override
    public boolean removeById(Serializable id) {
        boolean result = super.removeById(id);
        if (result) {
            evictListCache();
        }
        return result;
    }

    /**
     * 删除公告列表缓存（仅缓存了前 MAX_CACHED_PAGE 页，精确删除）
     */
    private void evictListCache() {
        try {
            stringRedisTemplate.delete(CacheKeys.noticeListKeys(MAX_CACHED_PAGE));
        } catch (Exception e) {
            log.warn("Redis 公告列表缓存删除失败", e);
        }
    }
}

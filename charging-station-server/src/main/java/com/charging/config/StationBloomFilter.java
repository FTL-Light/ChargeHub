package com.charging.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.charging.entity.Station;
import com.charging.mapper.StationMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 站点 ID 布隆过滤器（防止缓存穿透）
 *
 * <p>原理：基于 Redisson RBloomFilter（底层为 Redis BitMap + 多重哈希），
 * 启动时预加载全量站点 ID，读请求先经过过滤器：
 * <ul>
 *   <li>判定"一定不存在" → 直接返回，恶意伪造 ID 的请求打不到缓存和数据库</li>
 *   <li>判定"可能存在" → 放行至正常 缓存 → DB 链路（存在约 1% 误判率，误判只会多查一次 DB，无害）</li>
 * </ul>
 *
 * <p>注意：布隆过滤器不支持删除。站点被删除后通过全量重建保证一致性，
 * 站点删除是低频管理端操作，重建成本低。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StationBloomFilter {

    /**
     * 布隆过滤器在 Redis 中的 Key
     */
    public static final String BLOOM_KEY = "bloom:station:id";

    /**
     * 预期插入数量（预留扩容空间，避免误判率超标）
     */
    private static final long EXPECTED_INSERTIONS = 100_000L;

    /**
     * 误判率 1%
     */
    private static final double FALSE_POSITIVE_RATE = 0.01;

    private final RedissonClient redissonClient;
    private final StationMapper stationMapper;

    private RBloomFilter<Long> bloomFilter;

    /**
     * 就绪标志：Redis 异常或尚未完成加载时为 false，请求直接放行走降级链路
     */
    private volatile boolean ready = false;

    @PostConstruct
    public void init() {
        doInit(false);
    }

    /**
     * 判断站点 ID 是否"可能存在"（布隆过滤器语义）
     *
     * @return false 表示一定不存在（可安全拦截）；true 表示可能存在（放行）。
     *         过滤器不可用时始终返回 true，宁可多查一次 DB 也不能误杀正常请求。
     */
    public boolean mightContain(Long id) {
        if (!ready) {
            doInit(false);
            if (!ready) {
                return true;
            }
        }
        try {
            boolean contains = bloomFilter.contains(id);
            if (!contains && !bloomFilter.isExists()) {
                // Redis 中过滤器数据丢失（如 Redis 重启换库），重建后放行本次请求
                log.warn("布隆过滤器在 Redis 中丢失, 触发重建");
                rebuild();
                return true;
            }
            return contains;
        } catch (Exception e) {
            log.warn("布隆过滤器查询失败, 降级放行: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 新增站点 ID 加入过滤器
     */
    public void add(Long id) {
        try {
            if (ready) {
                bloomFilter.add(id);
            }
        } catch (Exception e) {
            log.warn("布隆过滤器写入失败, id={}: {}", id, e.getMessage());
        }
    }

    /**
     * 全量重建（布隆过滤器不支持删除，站点删除后调用以移除陈旧 ID）
     */
    public synchronized void rebuild() {
        doInit(true);
    }

    private void doInit(boolean forceRebuild) {
        try {
            bloomFilter = redissonClient.getBloomFilter(BLOOM_KEY);
            boolean initialized = forceRebuild
                    ? bloomFilter.delete() && bloomFilter.tryInit(EXPECTED_INSERTIONS, FALSE_POSITIVE_RATE)
                    : bloomFilter.tryInit(EXPECTED_INSERTIONS, FALSE_POSITIVE_RATE);

            if (initialized) {
                long count = stationMapper.selectList(
                                Wrappers.<Station>lambdaQuery().select(Station::getId)).stream()
                        .peek(station -> bloomFilter.add(station.getId()))
                        .count();
                log.info("布隆过滤器初始化完成, 共加载 {} 个站点 ID (预期容量={}, 误判率={})",
                        count, EXPECTED_INSERTIONS, FALSE_POSITIVE_RATE);
            }
            ready = true;
        } catch (Exception e) {
            ready = false;
            log.warn("布隆过滤器初始化失败, 暂时降级放行所有请求: {}", e.getMessage());
        }
    }
}

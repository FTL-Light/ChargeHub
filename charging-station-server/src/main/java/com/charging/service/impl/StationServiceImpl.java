package com.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.charging.common.CacheKeys;
import com.charging.config.StationBloomFilter;
import com.charging.entity.Charger;
import com.charging.entity.Station;
import com.charging.mapper.ChargerMapper;
import com.charging.mapper.StationMapper;
import com.charging.service.StationGeoService;
import com.charging.service.StationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.GeoShape;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 充电站服务实现类
 * 附近站点查询优先使用 Redis GEO(GEOSEARCH)，Redis 不可用时降级为数据库 + Haversine 计算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StationServiceImpl extends ServiceImpl<StationMapper, Station> implements StationService {

    /**
     * 地球半径(公里)
     */
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final StringRedisTemplate stringRedisTemplate;
    private final StationGeoService stationGeoService;
    private final StationBloomFilter stationBloomFilter;
    private final ChargerMapper chargerMapper;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;

    @Override
    public List<Station> findNearbyStations(Double longitude, Double latitude, Double radiusKm) {
        try {
            List<Station> result = findNearbyByGeo(longitude, latitude, radiusKm);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            log.warn("Redis GEO 查询失败, 降级为数据库计算: {}", e.getMessage());
        }
        return findNearbyByHaversine(longitude, latitude, radiusKm);
    }

    /**
     * 通过 Redis GEO 索引查询附近站点(按距离升序)
     *
     * @return 附近站点列表；GEO 索引为空(未初始化)时返回 null 以触发降级
     */
    private List<Station> findNearbyByGeo(Double longitude, Double latitude, Double radiusKm) {
        Long size = stringRedisTemplate.opsForZSet().zCard(StationGeoService.GEO_KEY);
        if (size == null || size == 0) {
            return null;
        }
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                StationGeoService.GEO_KEY,
                GeoReference.fromCoordinate(new Point(longitude, latitude)),
                GeoShape.byRadius(new Distance(radiusKm, Metrics.KILOMETERS)),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().sortAscending());
        if (results == null || results.getContent().isEmpty()) {
            return List.of();
        }
        List<Long> ids = results.getContent().stream()
                .map(GeoResult::getContent)
                .map(RedisGeoCommands.GeoLocation::getName)
                .map(Long::valueOf)
                .toList();
        // 按 ID 批量查询站点详情，再按 GEO 返回的距离顺序重排
        Map<Long, Station> stationMap = this.listByIds(ids).stream()
                .collect(Collectors.toMap(Station::getId, Function.identity()));
        return ids.stream()
                .map(stationMap::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * 降级方案：全表查询 + Haversine 公式计算距离并筛选排序
     */
    private List<Station> findNearbyByHaversine(Double longitude, Double latitude, Double radiusKm) {
        LambdaQueryWrapper<Station> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Station::getStatus, 1);
        List<Station> allStations = this.list(wrapper);

        return allStations.stream()
                .filter(station -> {
                    if (station.getLongitude() == null || station.getLatitude() == null) {
                        return false;
                    }
                    double distance = haversineDistance(latitude, longitude,
                            station.getLatitude().doubleValue(),
                            station.getLongitude().doubleValue());
                    return distance <= radiusKm;
                })
                .sorted(Comparator.comparingDouble(station ->
                        haversineDistance(latitude, longitude,
                                station.getLatitude().doubleValue(),
                                station.getLongitude().doubleValue())))
                .toList();
    }

    /**
     * 站点详情查询链路：布隆过滤器前置拦截 → Redis 缓存 → 互斥重建 → 数据库
     *
     * <p>三级缓存问题在此闭环：
     * <ul>
     *   <li>穿透：布隆过滤器拦截不存在的 ID</li>
     *   <li>雪崩：TTL 加 0~5 分钟随机抖动，避免同批 key 同时失效</li>
     *   <li>击穿：热点 key 失效瞬间只允许一个线程回源，其余线程短暂等待后双检命中新缓存</li>
     * </ul>
     */
    @Override
    public Station getCachedById(Long id) {
        if (!stationBloomFilter.mightContain(id)) {
            return null;
        }

        String key = CacheKeys.stationDetail(id);
        Station cached = readCache(key);
        if (cached != null) {
            return cached;
        }

        RLock lock = null;
        boolean locked = false;
        try {
            lock = redissonClient.getLock(CacheKeys.stationDetailLock(id));
            // 最多等待 500ms：等待期间持锁线程通常已重建完成，随后双检即可命中
            locked = lock.tryLock(500, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return loadFromDb(id, key);
        } catch (Exception e) {
            // Redis 不可用时不阻断读请求，直接回源
            log.warn("缓存重建锁不可用, 降级直查数据库, key={}", key, e);
            return loadFromDb(id, key);
        }

        try {
            if (locked) {
                // 双检：等锁期间缓存可能已被其它线程重建
                Station rebuilt = readCache(key);
                if (rebuilt != null) {
                    return rebuilt;
                }
            }
            return loadFromDb(id, key);
        } finally {
            if (locked) {
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (Exception e) {
                    log.warn("缓存重建锁释放失败, key={}", key, e);
                }
            }
        }
    }

    /**
     * 读缓存，任何异常都按未命中处理（缓存故障不能影响主流程）
     */
    private Station readCache(String key) {
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached == null) {
                return null;
            }
            return objectMapper.readValue(cached, Station.class);
        } catch (Exception e) {
            log.warn("Redis 站点详情缓存读取失败, key={}, 降级直查数据库", key, e);
            return null;
        }
    }

    /**
     * 回源数据库并回填缓存(带随机 TTL)
     */
    private Station loadFromDb(Long id, String key) {
        Station station = super.getById(id);
        if (station == null) {
            return null;
        }
        try {
            long expire = 30 * 60L + (long) (Math.random() * 5 * 60);
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(station), expire, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis 站点详情缓存写入失败, key={}", key, e);
        }
        return station;
    }

    @Override
    public boolean save(Station station) {
        boolean result = super.save(station);
        if (result) {
            stationBloomFilter.add(station.getId());
            stationGeoService.sync(station);
        }
        return result;
    }

    @Override
    public boolean updateById(Station station) {
        boolean result = super.updateById(station);
        if (result && station.getId() != null) {
            stringRedisTemplate.delete(CacheKeys.stationDetail(station.getId()));
            stationGeoService.sync(super.getById(station.getId()));
        }
        return result;
    }

    /**
     * 布隆过滤器不支持删除，站点删除后触发全量重建以移除陈旧 ID（低频管理端操作）
     */
    @Override
    public boolean removeById(Serializable id) {
        boolean result = super.removeById(id);
        if (result) {
            stringRedisTemplate.delete(CacheKeys.stationDetail((Long) id));
            stationGeoService.remove(id);
            stationBloomFilter.rebuild();
        }
        return result;
    }

    /**
     * 重算并回写站点充电桩数量(t_station.charger_count 为冗余字段，
     * 充电桩增删后调用，避免后台列表长期显示 0)
     */
    @Override
    public void refreshChargerCount(Long stationId) {
        if (stationId == null) {
            return;
        }
        try {
            Long count = chargerMapper.selectCount(
                    new LambdaQueryWrapper<Charger>().eq(Charger::getStationId, stationId));
            Station update = new Station();
            update.setId(stationId);
            update.setChargerCount(count == null ? 0 : count.intValue());
            this.updateById(update);
        } catch (Exception e) {
            log.warn("站点充电桩数量刷新失败, stationId={}", stationId, e);
        }
    }

    /**
     * Haversine 公式计算两点之间的球面距离(公里)
     *
     * @param lat1 纬度1
     * @param lon1 经度1
     * @param lat2 纬度2
     * @param lon2 经度2
     * @return 距离(公里)
     */
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(radLat1) * Math.cos(radLat2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }
}

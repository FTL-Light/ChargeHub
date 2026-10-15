package com.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.charging.entity.Station;
import com.charging.mapper.StationMapper;
import com.charging.service.StationGeoService;
import com.charging.service.StationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    public boolean save(Station station) {
        boolean result = super.save(station);
        if (result) {
            stationGeoService.sync(station);
        }
        return result;
    }

    @Override
    public boolean updateById(Station station) {
        boolean result = super.updateById(station);
        if (result && station.getId() != null) {
            // 入参可能是部分字段，查询最新完整实体后再同步索引
            stationGeoService.sync(super.getById(station.getId()));
        }
        return result;
    }

    @Override
    public boolean removeById(Serializable id) {
        boolean result = super.removeById(id);
        if (result) {
            stationGeoService.remove(id);
        }
        return result;
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

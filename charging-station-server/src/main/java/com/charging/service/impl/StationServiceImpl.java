package com.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.charging.entity.Station;
import com.charging.mapper.StationMapper;
import com.charging.service.StationService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 充电站服务实现类
 */
@Service
public class StationServiceImpl extends ServiceImpl<StationMapper, Station> implements StationService {

    /**
     * 地球半径(公里)
     */
    private static final double EARTH_RADIUS_KM = 6371.0;

    @Override
    public List<Station> findNearbyStations(Double longitude, Double latitude, Double radiusKm) {
        // 查询所有正常状态的站点
        LambdaQueryWrapper<Station> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Station::getStatus, 1);
        List<Station> allStations = this.list(wrapper);

        // 使用 Haversine 公式计算距离并筛选
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

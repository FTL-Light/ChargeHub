package com.charging.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.charging.entity.Station;

import java.util.List;

/**
 * 充电站服务接口
 */
public interface StationService extends IService<Station> {

    /**
     * 根据经纬度和半径查找附近站点 (Redis GEO，Redis 不可用时降级为 Haversine)
     *
     * @param longitude 经度
     * @param latitude  纬度
     * @param radiusKm  搜索半径(公里)
     * @return 附近站点列表(按距离升序)
     */
    List<Station> findNearbyStations(Double longitude, Double latitude, Double radiusKm);

    /**
     * 获取充电站详情（带缓存，旁路模式）
     *
     * @param id 站点ID
     * @return 站点详情；不存在返回 null
     */
    Station getCachedById(Long id);

    /**
     * 重算并回写站点下的充电桩数量(t_station.charger_count 为冗余字段)
     *
     * @param stationId 站点ID
     */
    void refreshChargerCount(Long stationId);
}

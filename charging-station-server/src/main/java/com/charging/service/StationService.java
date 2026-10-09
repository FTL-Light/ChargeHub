package com.charging.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.charging.entity.Station;

import java.util.List;

/**
 * 充电站服务接口
 */
public interface StationService extends IService<Station> {

    /**
     * 根据经纬度和半径查找附近站点 (Haversine 公式)
     *
     * @param longitude 经度
     * @param latitude  纬度
     * @param radiusKm  搜索半径(公里)
     * @return 附近站点列表
     */
    List<Station> findNearbyStations(Double longitude, Double latitude, Double radiusKm);
}

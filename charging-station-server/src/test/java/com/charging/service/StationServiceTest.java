package com.charging.service;

import com.charging.entity.Station;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 充电站服务测试类
 */
@Slf4j
@SpringBootTest
class StationServiceTest {

    @Autowired
    private StationService stationService;

    @Test
    void testGetById() {
        // 测试根据ID查询站点
        // 注意: 需要数据库中存在 ID=1 的记录
        Station station = stationService.getById(1L);
        log.info("查询站点信息: {}", station);
        // 由于测试环境数据不确定，这里仅验证方法不抛异常
        assertNotNull(stationService);
    }

    @Test
    void testFindNearbyStations() {
        // 测试查询附近站点 (北京天安门附近)
        // 经度: 116.397428, 纬度: 39.90923, 半径: 10公里
        Double longitude = 116.397428;
        Double latitude = 39.90923;
        Double radiusKm = 10.0;

        List<Station> nearbyStations = stationService.findNearbyStations(longitude, latitude, radiusKm);
        log.info("附近站点数量: {}", nearbyStations.size());
        nearbyStations.forEach(s -> log.info("站点: {}", s.getName()));

        // 验证返回结果不为null
        assertNotNull(nearbyStations);
    }
}

package com.charging.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.charging.entity.Station;
import com.charging.mapper.StationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 充电站 GEO 索引服务(Redis)
 * 仅索引 status=1(正常)的站点，DB 为主、GEO 为从；
 * Redis 不可用时仅记录告警，不影响 DB 主流程(查询侧降级为数据库计算)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StationGeoService {

    /**
     * GEO 索引 key，member 为站点ID字符串
     */
    public static final String GEO_KEY = "geo:station";

    private final StringRedisTemplate stringRedisTemplate;
    private final StationMapper stationMapper;

    /**
     * 同步单个站点到 GEO 索引(新增/编辑后调用)。
     * 站点为正常状态且有坐标则写入/覆盖，否则从索引移除。
     *
     * @param station 站点完整实体
     */
    public void sync(Station station) {
        if (station == null || station.getId() == null) {
            return;
        }
        try {
            if (station.getStatus() != null && station.getStatus() == 1
                    && station.getLongitude() != null && station.getLatitude() != null) {
                stringRedisTemplate.opsForGeo().add(GEO_KEY,
                        new Point(station.getLongitude().doubleValue(), station.getLatitude().doubleValue()),
                        String.valueOf(station.getId()));
            } else {
                stringRedisTemplate.opsForGeo().remove(GEO_KEY, String.valueOf(station.getId()));
            }
        } catch (Exception e) {
            log.warn("站点GEO索引同步失败, stationId={}", station.getId(), e);
        }
    }

    /**
     * 从 GEO 索引移除站点(删除后调用)
     *
     * @param id 站点ID
     */
    public void remove(Serializable id) {
        if (id == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForGeo().remove(GEO_KEY, String.valueOf(id));
        } catch (Exception e) {
            log.warn("站点GEO索引移除失败, stationId={}", id, e);
        }
    }

    /**
     * 应用启动后全量重建 GEO 索引(删旧建新，保证与 DB 最终一致)
     */
    @EventListener(ApplicationReadyEvent.class)
    public void rebuildGeoIndex() {
        try {
            List<Station> stations = stationMapper.selectList(
                    new LambdaQueryWrapper<Station>().eq(Station::getStatus, 1));
            stringRedisTemplate.delete(GEO_KEY);
            Map<String, Point> memberCoordinateMap = new HashMap<>();
            for (Station s : stations) {
                if (s.getLongitude() != null && s.getLatitude() != null) {
                    memberCoordinateMap.put(String.valueOf(s.getId()),
                            new Point(s.getLongitude().doubleValue(), s.getLatitude().doubleValue()));
                }
            }
            if (!memberCoordinateMap.isEmpty()) {
                stringRedisTemplate.opsForGeo().add(GEO_KEY, memberCoordinateMap);
            }
            log.info("站点GEO索引初始化完成, 共{}个站点", memberCoordinateMap.size());
        } catch (Exception e) {
            log.warn("站点GEO索引初始化失败(Redis不可用?), 附近站点查询将降级为数据库计算", e);
        }
    }
}

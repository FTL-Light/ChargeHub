package com.charging.controller.miniapp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.charging.common.exception.BusinessException;
import com.charging.common.result.R;
import com.charging.entity.Station;
import com.charging.service.StationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 小程序端-充电站控制器
 */
@Slf4j
@RestController
@RequestMapping("/miniapp/station")
@RequiredArgsConstructor
@Tag(name = "小程序端-充电站", description = "充电站相关接口")
public class MiniStationController {

    private final StationService stationService;

    @GetMapping("/list")
    @Operation(summary = "分页查询充电站列表")
    public R<Page<Station>> list(
            @Parameter(description = "当前页") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "城市") @RequestParam(required = false) String city,
            @Parameter(description = "关键字") @RequestParam(required = false) String keyword) {
        Page<Station> page = new Page<>(current, size);
        LambdaQueryWrapper<Station> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Station::getStatus, 1);
        wrapper.eq(city != null && !city.isEmpty(), Station::getCity, city);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Station::getName, keyword)
                    .or().like(Station::getAddress, keyword)
                    .or().like(Station::getDistrict, keyword));
        }
        wrapper.orderByDesc(Station::getCreateTime);
        return R.ok(stationService.page(page, wrapper));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取充电站详情")
    public R<Station> detail(@Parameter(description = "站点ID") @PathVariable Long id) {
        Station station = stationService.getById(id);
        if (station == null) {
            throw new BusinessException("站点不存在");
        }
        return R.ok(station);
    }

    @GetMapping("/nearby")
    @Operation(summary = "获取附近充电站")
    public R<List<Station>> nearby(
            @Parameter(description = "经度") @RequestParam Double longitude,
            @Parameter(description = "纬度") @RequestParam Double latitude,
            @Parameter(description = "搜索半径(公里)") @RequestParam(defaultValue = "5") Double radiusKm) {
        List<Station> stations = stationService.findNearbyStations(longitude, latitude, radiusKm);
        return R.ok(stations);
    }
}

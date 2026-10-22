package com.charging.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.charging.common.PageUtil;
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

/**
 * 管理端-充电站控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/station")
@RequiredArgsConstructor
@Tag(name = "管理端-充电站管理", description = "充电站相关接口")
public class AdminStationController {

    private final StationService stationService;

    @GetMapping("/list")
    @Operation(summary = "分页查询充电站列表")
    public R<Page<Station>> list(
            @Parameter(description = "当前页") @RequestParam(required = false) Integer current,
            @Parameter(description = "页码(兼容前端 page 参数)") @RequestParam(required = false) Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "站点名称") @RequestParam(required = false) String name,
            @Parameter(description = "城市") @RequestParam(required = false) String city,
            @Parameter(description = "状态") @RequestParam(required = false) Integer status) {
        Page<Station> pageResult = PageUtil.of(current, page, size);
        LambdaQueryWrapper<Station> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(name != null && !name.isEmpty(), Station::getName, name);
        wrapper.eq(city != null && !city.isEmpty(), Station::getCity, city);
        wrapper.eq(status != null, Station::getStatus, status);
        wrapper.orderByDesc(Station::getCreateTime);
        return R.ok(stationService.page(pageResult, wrapper));
    }

    @PostMapping
    @Operation(summary = "新增充电站")
    public R<Void> add(@RequestBody Station station) {
        station.setId(null);
        stationService.save(station);
        return R.ok();
    }

    @PutMapping
    @Operation(summary = "编辑充电站")
    public R<Void> update(@RequestBody Station station) {
        if (station.getId() == null) {
            throw new BusinessException("站点ID不能为空");
        }
        stationService.updateById(station);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除充电站")
    public R<Void> delete(@Parameter(description = "站点ID") @PathVariable Long id) {
        stationService.removeById(id);
        return R.ok();
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
}

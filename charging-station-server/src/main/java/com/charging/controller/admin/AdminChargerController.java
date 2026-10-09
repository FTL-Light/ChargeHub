package com.charging.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.charging.common.exception.BusinessException;
import com.charging.common.result.R;
import com.charging.entity.Charger;
import com.charging.service.ChargerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端-充电桩控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/charger")
@RequiredArgsConstructor
@Tag(name = "管理端-充电桩管理", description = "充电桩相关接口")
public class AdminChargerController {

    private final ChargerService chargerService;

    @GetMapping("/list")
    @Operation(summary = "分页查询充电桩列表")
    public R<Page<Charger>> list(
            @Parameter(description = "当前页") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "站点ID") @RequestParam(required = false) Long stationId,
            @Parameter(description = "类型") @RequestParam(required = false) Integer type,
            @Parameter(description = "状态") @RequestParam(required = false) Integer status) {
        Page<Charger> page = new Page<>(current, size);
        LambdaQueryWrapper<Charger> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(stationId != null, Charger::getStationId, stationId);
        wrapper.eq(type != null, Charger::getType, type);
        wrapper.eq(status != null, Charger::getStatus, status);
        wrapper.orderByDesc(Charger::getCreateTime);
        return R.ok(chargerService.page(page, wrapper));
    }

    @PostMapping
    @Operation(summary = "新增充电桩")
    public R<Void> add(@RequestBody Charger charger) {
        charger.setId(null);
        chargerService.save(charger);
        return R.ok();
    }

    @PutMapping
    @Operation(summary = "编辑充电桩")
    public R<Void> update(@RequestBody Charger charger) {
        if (charger.getId() == null) {
            throw new BusinessException("充电桩ID不能为空");
        }
        chargerService.updateById(charger);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除充电桩")
    public R<Void> delete(@Parameter(description = "充电桩ID") @PathVariable Long id) {
        chargerService.removeById(id);
        return R.ok();
    }
}

package com.charging.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.charging.common.PageUtil;
import com.charging.common.exception.BusinessException;
import com.charging.common.result.R;
import com.charging.entity.Charger;
import com.charging.entity.Gun;
import com.charging.service.ChargerService;
import com.charging.service.GunService;
import com.charging.service.StationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
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
    private final GunService gunService;
    private final StationService stationService;

    @GetMapping("/list")
    @Operation(summary = "分页查询充电桩列表")
    public R<Page<Charger>> list(
            @Parameter(description = "当前页") @RequestParam(required = false) Integer current,
            @Parameter(description = "页码(兼容前端 page 参数)") @RequestParam(required = false) Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "站点ID") @RequestParam(required = false) Long stationId,
            @Parameter(description = "类型") @RequestParam(required = false) Integer type,
            @Parameter(description = "状态") @RequestParam(required = false) Integer status) {
        Page<Charger> pageResult = PageUtil.of(current, page, size);
        LambdaQueryWrapper<Charger> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(stationId != null, Charger::getStationId, stationId);
        wrapper.eq(type != null, Charger::getType, type);
        wrapper.eq(status != null, Charger::getStatus, status);
        wrapper.orderByDesc(Charger::getCreateTime);
        return R.ok(chargerService.page(pageResult, wrapper));
    }

    @PostMapping
    @Operation(summary = "新增充电桩")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> add(@RequestBody Charger charger) {
        charger.setId(null);
        // 新建桩默认空闲，避免前端不传状态时落库为 null 被判为故障
        if (charger.getStatus() == null) {
            charger.setStatus(1);
        }
        if (charger.getGunCount() == null || charger.getGunCount() < 1) {
            charger.setGunCount(1);
        }
        chargerService.save(charger);
        // 按枪数自动创建充电枪：没有枪的桩无法被扫码启动，避免"新建桩不可用"
        int gunCount = charger.getGunCount();
        for (int i = 1; i <= gunCount; i++) {
            Gun gun = new Gun();
            gun.setChargerId(charger.getId());
            gun.setCode(charger.getCode() + "-G" + i);
            gun.setStatus(1);
            gunService.save(gun);
        }
        // 同步站点下的充电桩数量(冗余字段，供列表展示)
        stationService.refreshChargerCount(charger.getStationId());
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
        Charger charger = chargerService.getById(id);
        chargerService.removeById(id);
        if (charger != null) {
            stationService.refreshChargerCount(charger.getStationId());
        }
        return R.ok();
    }
}

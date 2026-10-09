package com.charging.controller.miniapp;

import com.charging.common.exception.BusinessException;
import com.charging.common.result.R;
import com.charging.entity.Charger;
import com.charging.entity.Gun;
import com.charging.service.ChargerService;
import com.charging.service.GunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 小程序端-充电桩控制器
 */
@Slf4j
@RestController
@RequestMapping("/miniapp/charger")
@RequiredArgsConstructor
@Tag(name = "小程序端-充电桩", description = "充电桩相关接口")
public class MiniChargerController {

    private final ChargerService chargerService;
    private final GunService gunService;

    @GetMapping("/{id}")
    @Operation(summary = "获取充电桩详情(包含枪列表)")
    public R<Map<String, Object>> detail(@Parameter(description = "充电桩ID") @PathVariable Long id) {
        Charger charger = chargerService.getById(id);
        if (charger == null) {
            throw new BusinessException("充电桩不存在");
        }
        // 查询该充电桩下所有枪
        List<Gun> guns = gunService.lambdaQuery()
                .eq(Gun::getChargerId, id)
                .orderByAsc(Gun::getCode)
                .list();

        Map<String, Object> result = new HashMap<>();
        result.put("charger", charger);
        result.put("guns", guns);
        return R.ok(result);
    }
}

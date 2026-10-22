package com.charging.controller.miniapp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.charging.common.PageUtil;
import com.charging.common.enums.OrderStatus;
import com.charging.common.exception.BusinessException;
import com.charging.common.result.R;
import com.charging.dto.StartChargingDTO;
import com.charging.dto.StopChargingDTO;
import com.charging.entity.Charger;
import com.charging.entity.Order;
import com.charging.service.ChargingService;
import com.charging.service.OrderService;
import com.charging.service.ChargerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 小程序端-订单控制器
 *
 * <p>只做参数接收与响应封装，业务逻辑（锁/事务/状态流转）下沉到 {@link ChargingService}。
 * 这样做的直接原因：分布式锁必须在事务外层，而 @Transactional 加在控制器方法上时，
 * 事务提交发生在方法返回之后，锁的释放必然早于提交。
 */
@Slf4j
@RestController
@RequestMapping("/miniapp/order")
@RequiredArgsConstructor
@Tag(name = "小程序端-订单", description = "订单相关接口")
public class MiniOrderController {

    private final OrderService orderService;
    private final ChargerService chargerService;
    private final ChargingService chargingService;

    @PostMapping("/start")
    @Operation(summary = "开始充电")
    public R<Order> start(@Valid @RequestBody StartChargingDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Order order = chargingService.startCharging(userId, dto);
        return R.ok("开始充电", order);
    }

    @PostMapping("/stop")
    @Operation(summary = "停止充电")
    public R<Order> stop(@Valid @RequestBody StopChargingDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Order order = chargingService.stopCharging(userId, dto);
        return R.ok("充电结束", order);
    }

    @GetMapping("/charging-status")
    @Operation(summary = "获取充电状态")
    public R<Order> chargingStatus(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Order order = orderService.lambdaQuery()
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, OrderStatus.CHARGING.getCode())
                .last("LIMIT 1")
                .one();
        return R.ok(order);
    }

    @GetMapping("/list")
    @Operation(summary = "我的订单列表")
    public R<Page<Order>> list(
            @Parameter(description = "当前页") @RequestParam(required = false) Integer current,
            @Parameter(description = "页码(兼容前端 page 参数)") @RequestParam(required = false) Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "状态") @RequestParam(required = false) Integer status,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Page<Order> pageResult = PageUtil.of(current, page, size);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId);
        wrapper.eq(status != null, Order::getStatus, status);
        wrapper.orderByDesc(Order::getCreateTime);
        return R.ok(orderService.page(pageResult, wrapper));
    }

    @GetMapping("/{id}")
    @Operation(summary = "订单详情")
    public R<Map<String, Object>> detail(@Parameter(description = "订单ID") @PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Order order = orderService.getById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权查看此订单");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        // 关联充电桩信息
        Charger charger = chargerService.getById(order.getChargerId());
        result.put("charger", charger);
        return R.ok(result);
    }
}

package com.charging.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.charging.common.result.R;
import com.charging.entity.Order;
import com.charging.entity.User;
import com.charging.service.OrderService;
import com.charging.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端-订单控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/order")
@RequiredArgsConstructor
@Tag(name = "管理端-订单管理", description = "订单相关接口")
public class AdminOrderController {

    private final OrderService orderService;
    private final UserService userService;

    @GetMapping("/list")
    @Operation(summary = "分页查询订单列表")
    public R<Page<Order>> list(
            @Parameter(description = "当前页") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "订单号") @RequestParam(required = false) String orderNo,
            @Parameter(description = "手机号") @RequestParam(required = false) String phone,
            @Parameter(description = "状态") @RequestParam(required = false) Integer status,
            @Parameter(description = "站点ID") @RequestParam(required = false) Long stationId,
            @Parameter(description = "开始时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        Page<Order> page = new Page<>(current, size);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(orderNo != null && !orderNo.isEmpty(), Order::getOrderNo, orderNo);
        wrapper.eq(status != null, Order::getStatus, status);
        wrapper.eq(stationId != null, Order::getStationId, stationId);
        wrapper.ge(startTime != null, Order::getCreateTime, startTime);
        wrapper.le(endTime != null, Order::getCreateTime, endTime);

        // 按手机号筛选: 先查用户ID
        if (phone != null && !phone.isEmpty()) {
            List<User> users = userService.lambdaQuery()
                    .like(User::getPhone, phone)
                    .list();
            if (users.isEmpty()) {
                return R.ok(page);
            }
            List<Long> userIds = users.stream().map(User::getId).collect(Collectors.toList());
            wrapper.in(Order::getUserId, userIds);
        }

        wrapper.orderByDesc(Order::getCreateTime);
        return R.ok(orderService.page(page, wrapper));
    }

    @GetMapping("/statistics")
    @Operation(summary = "获取订单统计数据")
    public R<Map<String, Object>> statistics() {
        return R.ok(orderService.getStatistics());
    }
}

package com.charging.controller.miniapp;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.charging.common.exception.BusinessException;
import com.charging.common.result.R;
import com.charging.dto.StartChargingDTO;
import com.charging.dto.StopChargingDTO;
import com.charging.entity.*;
import com.charging.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 小程序端-订单控制器
 */
@Slf4j
@RestController
@RequestMapping("/miniapp/order")
@RequiredArgsConstructor
@Tag(name = "小程序端-订单", description = "订单相关接口")
public class MiniOrderController {

    private final OrderService orderService;
    private final ChargerService chargerService;
    private final GunService gunService;
    private final UserService userService;

    @PostMapping("/start")
    @Operation(summary = "开始充电")
    @Transactional(rollbackFor = Exception.class)
    public R<Order> start(@Valid @RequestBody StartChargingDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        // 锁定用户行，串行化同一用户的并发下单，并校验账号状态
        User user = userService.lambdaQuery()
                .eq(User::getId, userId)
                .last("FOR UPDATE")
                .one();
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用，无法充电");
        }

        // 校验充电桩
        Charger charger = chargerService.getById(dto.getChargerId());
        if (charger == null) {
            throw new BusinessException("充电桩不存在");
        }
        if (charger.getStatus() == null || charger.getStatus() == 0) {
            throw new BusinessException("充电桩故障，无法使用");
        }
        if (charger.getStatus() == 2) {
            throw new BusinessException("充电桩使用中，请选择其他充电桩");
        }

        // 校验充电枪
        Gun gun = gunService.getById(dto.getGunId());
        if (gun == null) {
            throw new BusinessException("充电枪不存在");
        }
        if (gun.getStatus() == null || gun.getStatus() == 0) {
            throw new BusinessException("充电枪故障，无法使用");
        }
        if (gun.getStatus() == 2) {
            throw new BusinessException("充电枪使用中，请选择其他充电枪");
        }

        // 检查用户是否已有充电中的订单
        long chargingCount = orderService.lambdaQuery()
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, 1)
                .count();
        if (chargingCount > 0) {
            throw new BusinessException("您有正在进行的充电订单，请先结束");
        }

        // CAS 锁定充电枪(仅当仍为空闲时更新成功)，防止并发抢占同一把枪
        boolean gunLocked = gunService.lambdaUpdate()
                .eq(Gun::getId, gun.getId())
                .eq(Gun::getStatus, 1)
                .set(Gun::getStatus, 2)
                .update();
        if (!gunLocked) {
            throw new BusinessException("充电枪已被占用，请选择其他充电枪");
        }

        // CAS 锁定充电桩，失败则抛异常回滚(释放上面锁定的枪)
        boolean chargerLocked = chargerService.lambdaUpdate()
                .eq(Charger::getId, charger.getId())
                .eq(Charger::getStatus, 1)
                .set(Charger::getStatus, 2)
                .update();
        if (!chargerLocked) {
            throw new BusinessException("充电桩已被占用，请选择其他充电桩");
        }

        // 生成订单编号: ORD + 时间戳 + 4位随机数
        String orderNo = "ORD" + System.currentTimeMillis() + RandomUtil.randomNumbers(4);

        // 创建订单
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setStationId(dto.getStationId());
        order.setChargerId(dto.getChargerId());
        order.setGunId(dto.getGunId());
        order.setStartTime(LocalDateTime.now());
        order.setPrice(charger.getPrice());
        order.setPayType(dto.getPayType() != null ? dto.getPayType() : 1);
        order.setStatus(1); // 充电中
        orderService.save(order);

        return R.ok("开始充电", order);
    }

    @PostMapping("/stop")
    @Operation(summary = "停止充电")
    @Transactional(rollbackFor = Exception.class)
    public R<Order> stop(@Valid @RequestBody StopChargingDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        // 行锁读取订单，串行化同一订单的并发结算，防止重复扣款
        Order order = orderService.lambdaQuery()
                .eq(Order::getId, dto.getOrderId())
                .last("FOR UPDATE")
                .one();
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此订单");
        }
        if (order.getStatus() != 1) {
            throw new BusinessException("订单状态不允许停止充电");
        }

        // 计算充电时长(分钟)
        LocalDateTime endTime = LocalDateTime.now();
        long minutes = Duration.between(order.getStartTime(), endTime).toMinutes();
        if (minutes < 1) {
            minutes = 1;
        }

        // 获取充电桩信息计算电量
        Charger charger = chargerService.getById(order.getChargerId());
        // 电量(度) = 功率(kW) * 时间(h)
        BigDecimal electricity = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (charger != null && charger.getPower() != null) {
            BigDecimal hours = new BigDecimal(minutes).divide(new BigDecimal("60"), 4, RoundingMode.HALF_UP);
            electricity = charger.getPower().multiply(hours).setScale(2, RoundingMode.HALF_UP);
            // 总金额 = 电量 * 单价
            BigDecimal price = order.getPrice() != null ? order.getPrice() : charger.getPrice();
            totalAmount = electricity.multiply(price).setScale(2, RoundingMode.HALF_UP);
        }

        // 更新订单
        order.setEndTime(endTime);
        order.setDuration((int) minutes);
        order.setElectricity(electricity);
        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount);

        Integer payType = dto.getPayType() != null ? dto.getPayType() : order.getPayType();
        order.setPayType(payType);

        if (payType != null && payType == 1) {
            // 原子扣减余额(数据库层面扣减)，余额不足则失败并回滚整个事务
            boolean deducted = userService.deductBalance(userId, totalAmount);
            if (!deducted) {
                throw new BusinessException("余额不足，请充值或选择其他支付方式");
            }
            order.setStatus(3); // 已完成
        } else {
            order.setStatus(2); // 待支付
        }
        orderService.updateById(order);

        // 恢复充电桩和枪状态为 1(空闲)
        Charger chargerUpdate = new Charger();
        chargerUpdate.setId(order.getChargerId());
        chargerUpdate.setStatus(1);
        chargerService.updateById(chargerUpdate);

        Gun gunUpdate = new Gun();
        gunUpdate.setId(order.getGunId());
        gunUpdate.setStatus(1);
        gunService.updateById(gunUpdate);

        return R.ok("充电结束", order);
    }

    @GetMapping("/charging-status")
    @Operation(summary = "获取充电状态")
    public R<Order> chargingStatus(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Order order = orderService.lambdaQuery()
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, 1)
                .last("LIMIT 1")
                .one();
        return R.ok(order);
    }

    @GetMapping("/list")
    @Operation(summary = "我的订单列表")
    public R<Page<Order>> list(
            @Parameter(description = "当前页") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "状态") @RequestParam(required = false) Integer status,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Page<Order> page = new Page<>(current, size);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId);
        wrapper.eq(status != null, Order::getStatus, status);
        wrapper.orderByDesc(Order::getCreateTime);
        return R.ok(orderService.page(page, wrapper));
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

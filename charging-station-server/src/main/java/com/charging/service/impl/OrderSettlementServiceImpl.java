package com.charging.service.impl;

import com.charging.common.enums.OrderSettleType;
import com.charging.common.enums.OrderStatus;
import com.charging.common.exception.BusinessException;
import com.charging.entity.Charger;
import com.charging.entity.Gun;
import com.charging.entity.Order;
import com.charging.service.ChargerService;
import com.charging.service.GunService;
import com.charging.service.OrderSettlementService;
import com.charging.service.OrderService;
import com.charging.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 订单结算服务实现：主动停充与超时结算共用同一套计费与资源释放逻辑
 *
 * <p>调用方必须以 {@code SELECT ... FOR UPDATE} 锁定订单行后再进入本类，
 * 并在外层事务中执行（这里的方法本身不加 {@code @Transactional}，
 * 因为需要把多个写操作（订单、用户余额、设备状态）放进同一个事务统一管理）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSettlementServiceImpl implements OrderSettlementService {

    private final OrderService orderService;
    private final ChargerService chargerService;
    private final GunService gunService;
    private final UserService userService;

    /** 单次充电最长时长(分钟)，超出部分不计入账单，防止设备侧异常产生天价金额 */
    @Value("${order.max-charging-minutes:720}")
    private long maxChargingMinutes;

    @Override
    public Order settleByUser(Order order, LocalDateTime endTime, Integer payType) {
        return settle(order, endTime, payType, true, OrderSettleType.MANUAL.getCode());
    }

    @Override
    public Order settleByTimeout(Order order, LocalDateTime endTime, int settleType) {
        return settle(order, endTime, null, false, settleType);
    }

    /**
     * 统一结算入口
     *
     * @param manual true 表示用户主动触发（严格），false 表示系统自动收敛（尽可能释放资源）
     */
    private Order settle(Order order, LocalDateTime endTime, Integer payType, boolean manual, int settleType) {
        Charger charger = chargerService.getById(order.getChargerId());

        // 计费参数缺失：用户主动结算直接拒绝；自动结算则按最小口径兜底，
        // 因为"设备被永久占用"的代价远大于"这笔少收"。
        if (charger == null || charger.getPower() == null || charger.getPower().compareTo(BigDecimal.ZERO) <= 0) {
            if (manual) {
                throw new BusinessException("充电桩计费参数异常，请联系管理员处理");
            }
            log.error("充电桩计费参数缺失，超时订单按最小口径结算并强制释放设备, orderId={}, chargerId={}",
                    order.getId(), order.getChargerId());
            finishZeroAmount(order, settleType);
            releaseResources(order, manual);
            return order;
        }

        // 计费时长：不足 1 分钟按 1 分钟计；超过单次上限则封顶，避免设备端时钟异常导致天价账单
        long minutes = Duration.between(order.getStartTime(), endTime).toMinutes();
        if (minutes < 1) {
            minutes = 1;
        }
        if (minutes > maxChargingMinutes) {
            log.warn("充电时长超过上限 {} 分钟，已封顶计费, orderId={}, actual={}", maxChargingMinutes, order.getId(), minutes);
            minutes = maxChargingMinutes;
        }

        // 电量(度) = 功率(kW) × 时间(h)
        BigDecimal hours = new BigDecimal(minutes).divide(new BigDecimal("60"), 4, RoundingMode.HALF_UP);
        BigDecimal electricity = charger.getPower().multiply(hours).setScale(2, RoundingMode.HALF_UP);
        // 总金额 = 电量 × 单价（优先取订单快照单价，防止调价影响进行中的订单）
        BigDecimal price = order.getPrice() != null ? order.getPrice() : charger.getPrice();
        BigDecimal totalAmount = electricity.multiply(price).setScale(2, RoundingMode.HALF_UP);

        order.setEndTime(endTime);
        order.setDuration((int) minutes);
        order.setElectricity(electricity);
        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount);
        order.setSettleType(settleType);

        int effectivePayType = payType != null ? payType : (order.getPayType() != null ? order.getPayType() : 1);
        // 微信支付通道未接入：用户主动选择时直接拒绝；
        // 自动链路遇到微信支付订单则挂账为"待支付"，由关单延迟消息负责收敛。
        if (effectivePayType == 2) {
            if (manual) {
                throw new BusinessException("暂不支持微信支付，请使用余额支付");
            }
            order.setPayType(effectivePayType);
            OrderStatus.transit(order, OrderStatus.UNPAID);
            updateOrder(order);
            releaseResources(order, manual);
            return order;
        }
        order.setPayType(effectivePayType);

        boolean deducted = userService.deductBalance(order.getUserId(), totalAmount);
        if (!deducted) {
            if (manual) {
                throw new BusinessException("余额不足，请充值或选择其他支付方式");
            }
            // 自动链路：扣不动就挂账，设备照常释放，后续由支付方式超时关单流程处理
            log.warn("超时订单余额不足，转为待支付, orderId={}, amount={}", order.getId(), totalAmount);
            OrderStatus.transit(order, OrderStatus.UNPAID);
            updateOrder(order);
            releaseResources(order, manual);
            return order;
        }

        OrderStatus.transit(order, OrderStatus.COMPLETED);
        updateOrder(order);
        releaseResources(order, manual);

        log.info("订单结算完成, orderId={}, minutes={}, electricity={}, amount={}, status={}, settleType={}",
                order.getId(), minutes, electricity, totalAmount, order.getStatus(), settleType);
        return order;
    }

    @Override
    public void releaseResourcesIfOccupied(Order order) {
        // false 表示非用户主动触发：异常只记日志，不向上抛（自动链路不能中断）
        releaseResources(order, false);
    }

    /**
     * 设备侧无法计费时的兜底：金额置 0 直接完成，优先保证资源回收
     */
    private void finishZeroAmount(Order order, int settleType) {
        LocalDateTime endTime = LocalDateTime.now();
        long minutes = Duration.between(order.getStartTime(), endTime).toMinutes();
        order.setEndTime(endTime);
        order.setDuration((int) Math.max(minutes, 1));
        order.setElectricity(BigDecimal.ZERO);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setPayAmount(BigDecimal.ZERO);
        order.setSettleType(settleType);
        OrderStatus.transit(order, OrderStatus.COMPLETED);
        updateOrder(order);
    }

    /**
     * 乐观锁更新订单，失败必须中断，不能静默放过
     */
    private void updateOrder(Order order) {
        if (!orderService.updateById(order)) {
            throw new BusinessException("订单已被其他操作修改，请刷新后重试");
        }
    }

    /**
     * 释放充电桩与充电枪（CAS 幂等）
     *
     * <p>CAS 返回 false 时先看当前真实状态：已经是"空闲"说明被别的流程释放过，视为成功；
     * 否则才是真的异常。这样幂等重放不会误报失败。
     */
    private void releaseResources(Order order, boolean manual) {
        boolean chargerReleased = chargerService.lambdaUpdate()
                .eq(Charger::getId, order.getChargerId())
                .eq(Charger::getStatus, 2)
                .set(Charger::getStatus, 1)
                .update();
        if (!chargerReleased) {
            Charger current = chargerService.getById(order.getChargerId());
            boolean alreadyIdle = current != null && Integer.valueOf(1).equals(current.getStatus());
            if (alreadyIdle) {
                log.info("充电桩已被释放，跳过(CAS 幂等), chargerId={}", order.getChargerId());
            } else if (manual) {
                throw new BusinessException("充电桩状态异常，停止充电失败");
            } else {
                log.error("超时结算释放充电桩失败, chargerId={}, 需人工介入", order.getChargerId());
            }
        }

        boolean gunReleased = gunService.lambdaUpdate()
                .eq(Gun::getId, order.getGunId())
                .eq(Gun::getStatus, 2)
                .set(Gun::getStatus, 1)
                .update();
        if (!gunReleased) {
            Gun current = gunService.getById(order.getGunId());
            boolean alreadyIdle = current != null && Integer.valueOf(1).equals(current.getStatus());
            if (alreadyIdle) {
                log.info("充电枪已被释放，跳过(CAS 幂等), gunId={}", order.getGunId());
            } else if (manual) {
                throw new BusinessException("充电枪状态异常，停止充电失败");
            } else {
                log.error("超时结算释放充电枪失败, gunId={}, 需人工介入", order.getGunId());
            }
        }
    }
}

package com.charging.controller.miniapp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.charging.common.exception.BusinessException;
import com.charging.common.result.R;
import com.charging.entity.RechargeRecord;
import com.charging.entity.User;
import com.charging.service.RechargeRecordService;
import com.charging.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 小程序端-充值控制器
 */
@Slf4j
@RestController
@RequestMapping("/miniapp/recharge")
@RequiredArgsConstructor
@Validated
@Tag(name = "小程序端-充值", description = "充值相关接口")
public class MiniRechargeController {

    private final RechargeRecordService rechargeRecordService;
    private final UserService userService;

    @PostMapping
    @Operation(summary = "充值")
    public R<RechargeRecord> recharge(@Valid @RequestBody RechargeDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("充值金额必须大于0");
        }

        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 创建充值记录
        RechargeRecord record = new RechargeRecord();
        record.setUserId(userId);
        record.setAmount(dto.getAmount());
        record.setPayType(dto.getPayType() != null ? dto.getPayType() : 1);
        record.setStatus(1); // 成功(模拟支付成功)
        rechargeRecordService.save(record);

        // 更新用户余额
        BigDecimal newBalance = (user.getBalance() == null ? BigDecimal.ZERO : user.getBalance()).add(dto.getAmount());
        User update = new User();
        update.setId(userId);
        update.setBalance(newBalance);
        userService.updateById(update);

        return R.ok("充值成功", record);
    }

    @GetMapping("/list")
    @Operation(summary = "充值记录列表")
    public R<Page<RechargeRecord>> list(
            @Parameter(description = "当前页") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Page<RechargeRecord> page = new Page<>(current, size);
        LambdaQueryWrapper<RechargeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RechargeRecord::getUserId, userId);
        wrapper.orderByDesc(RechargeRecord::getCreateTime);
        return R.ok(rechargeRecordService.page(page, wrapper));
    }

    /**
     * 充值请求参数
     */
    @Data
    public static class RechargeDTO {
        @NotNull(message = "充值金额不能为空")
        private BigDecimal amount;

        private Integer payType;
    }
}

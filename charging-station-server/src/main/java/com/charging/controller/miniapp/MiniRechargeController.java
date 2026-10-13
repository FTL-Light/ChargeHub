package com.charging.controller.miniapp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.charging.common.exception.BusinessException;
import com.charging.common.result.R;
import com.charging.entity.RechargeRecord;
import com.charging.service.RechargeRecordService;
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

    @PostMapping
    @Operation(summary = "充值")
    public R<RechargeRecord> recharge(@Valid @RequestBody RechargeDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("充值金额必须大于0");
        }

        // 事务内完成: 写充值记录 + 原子增加余额，requestNo 保证幂等
        RechargeRecord record = rechargeRecordService.recharge(userId, dto.getAmount(), dto.getPayType(), dto.getRequestNo());
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

        /**
         * 幂等请求号(前端生成，同一请求号只充值一次)
         */
        private String requestNo;
    }
}

package com.charging.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.charging.common.exception.BusinessException;
import com.charging.entity.RechargeRecord;
import com.charging.entity.User;
import com.charging.mapper.RechargeRecordMapper;
import com.charging.service.RechargeRecordService;
import com.charging.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 充值记录服务实现类
 *
 * <p>安全边界（金额由服务端裁决，不接受客户端任意值）：
 * <ol>
 *   <li>金额必须命中配置的充值档位 pay.amount-options</li>
 *   <li>单用户单日累计充值不超过 pay.daily-limit</li>
 *   <li>requestNo 必填，配合 t_recharge_record.uk_request_no 唯一索引保证幂等</li>
 *   <li>pay.mock-enabled=false 时拒绝"模拟支付"，避免生产环境白送余额</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeRecordServiceImpl extends ServiceImpl<RechargeRecordMapper, RechargeRecord> implements RechargeRecordService {

    private final UserService userService;

    /**
     * 是否允许模拟支付(仅本地/演示环境开启)
     */
    @Value("${pay.mock-enabled:true}")
    private boolean mockEnabled;

    /**
     * 允许的充值档位(元)，逗号分隔
     */
    @Value("${pay.amount-options:10,50,100,200,500}")
    private String amountOptions;

    /**
     * 单用户单日累计充值上限(元)
     */
    @Value("${pay.daily-limit:2000}")
    private BigDecimal dailyLimit;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RechargeRecord recharge(Long userId, BigDecimal amount, Integer payType, String requestNo) {
        if (requestNo == null || requestNo.isBlank()) {
            throw new BusinessException("缺少充值请求号，无法保证幂等");
        }

        // 幂等校验：同一请求号直接返回已有记录，防止网络重试导致重复充值
        RechargeRecord existing = this.lambdaQuery()
                .eq(RechargeRecord::getRequestNo, requestNo)
                .one();
        if (existing != null) {
            return existing;
        }

        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用，无法充值");
        }

        // 金额合法性由服务端裁决，不接受客户端任意数值
        validateAmount(userId, amount);

        if (!mockEnabled) {
            throw new BusinessException("支付通道未接入，请联系管理员");
        }

        // 创建充值记录
        RechargeRecord record = new RechargeRecord();
        record.setUserId(userId);
        record.setAmount(amount);
        record.setPayType(payType != null ? payType : 1);
        record.setRequestNo(requestNo);
        // 模拟支付：仅当 pay.mock-enabled=true 时直接置为成功
        record.setStatus(1);
        try {
            this.save(record);
        } catch (DuplicateKeyException e) {
            // 并发重复提交命中唯一索引，返回已存在的记录
            RechargeRecord duplicated = this.lambdaQuery()
                    .eq(RechargeRecord::getRequestNo, requestNo)
                    .one();
            if (duplicated != null) {
                return duplicated;
            }
            throw new BusinessException("充值重复提交，请稍后再试");
        }

        // 原子增加余额(数据库层面自增，避免并发丢失更新)
        boolean updated = userService.addBalance(userId, amount);
        if (!updated) {
            throw new BusinessException("充值失败，请稍后再试");
        }
        log.info("充值成功(模拟支付), userId={}, amount={}, requestNo={}", userId, amount, requestNo);
        return record;
    }

    /**
     * 校验充值金额：档位白名单 + 单日累计上限
     *
     * @param userId 用户ID
     * @param amount 充值金额
     */
    private void validateAmount(Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("充值金额必须大于0");
        }
        List<BigDecimal> options = parseAmountOptions();
        boolean matched = options.stream().anyMatch(option -> option.compareTo(amount) == 0);
        if (!matched) {
            String optionsText = options.stream()
                    .map(BigDecimal::stripTrailingZeros)
                    .map(BigDecimal::toPlainString)
                    .collect(Collectors.joining("/"));
            throw new BusinessException("充值金额必须为以下档位之一: " + optionsText + " 元");
        }

        BigDecimal todayAmount = baseMapper.sumSuccessAmountSince(userId, LocalDate.now().atStartOfDay());
        BigDecimal used = todayAmount == null ? BigDecimal.ZERO : todayAmount;
        if (used.add(amount).compareTo(dailyLimit) > 0) {
            throw new BusinessException("单日累计充值不能超过 " + dailyLimit.stripTrailingZeros().toPlainString() + " 元");
        }
    }

    /**
     * 解析配置的充值档位
     */
    private List<BigDecimal> parseAmountOptions() {
        List<BigDecimal> options = Arrays.stream(amountOptions.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(BigDecimal::new)
                .toList();
        if (options.isEmpty()) {
            throw new BusinessException("充值档位未配置，请联系管理员");
        }
        return options;
    }
}

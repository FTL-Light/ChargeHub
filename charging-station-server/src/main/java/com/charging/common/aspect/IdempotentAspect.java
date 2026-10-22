package com.charging.common.aspect;

import com.charging.common.annotation.Idempotent;
import com.charging.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 幂等切面
 *
 * <p>执行顺序设为最高优先级，确保幂等校验在事务/分布式锁之前完成，
 * 避免重复请求开启事务、占用连接。
 *
 * <p>失败策略：业务抛异常时释放占位，允许用户重试；成功则保留到 TTL 到期，
 * 防止"成功后前端重试"造成二次提交。
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class IdempotentAspect {

    private static final String KEY_PREFIX = "idem:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /**
     * 编译期缓存 SpEL 解析结果，避免每次请求重复解析
     */
    private final ConcurrentHashMap<String, Expression> expressionCache = new ConcurrentHashMap<>();

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object bizKey = evaluateKey(method, joinPoint.getArgs(), idempotent.key());
        if (bizKey == null || bizKey.toString().isBlank()) {
            // 无法确定幂等键时不阻断业务，交由数据库唯一索引兜底
            return joinPoint.proceed();
        }

        String redisKey = KEY_PREFIX + idempotent.prefix() + ":" + bizKey;
        boolean acquired;
        try {
            acquired = Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(redisKey, "1", Duration.ofSeconds(idempotent.ttl())));
        } catch (Exception e) {
            // Redis 不可用时降级放行：正确性由唯一索引 + 状态机保证，可用性优先
            log.warn("幂等校验降级(Redis 不可用), key={}", redisKey, e);
            return joinPoint.proceed();
        }

        if (!acquired) {
            log.info("拦截重复提交, key={}", redisKey);
            throw new BusinessException(idempotent.message());
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            // 业务失败释放占位，否则用户在 TTL 窗口内无法修正参数重试
            safeRelease(redisKey);
            throw t;
        }
    }

    /**
     * 解析 SpEL 幂等键
     */
    private Object evaluateKey(Method method, Object[] args, String expression) {
        try {
            Expression exp = expressionCache.computeIfAbsent(method.toGenericString() + "|" + expression,
                    k -> expressionParser.parseExpression(expression));
            return exp.getValue(new MethodBasedEvaluationContext(null, method, args, parameterNameDiscoverer));
        } catch (Exception e) {
            log.warn("幂等键解析失败, expression={}", expression, e);
            return null;
        }
    }

    private void safeRelease(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("幂等占位释放失败(将等待 TTL 自动过期), key={}", key, e);
        }
    }
}

package com.charging.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口幂等注解
 *
 * <p>基于 Redis SETNX 做"短窗口去重"，拦截用户连点、网络重试、网关重放造成的重复提交。
 * 与数据库唯一索引是**互补**关系：
 * <ul>
 *   <li>Redis 拦住 99% 的重复流量，避免无效事务打到数据库</li>
 *   <li>唯一索引是最后一道防线，Redis 不可用或 key 过期后仍能兜底</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 *   &#64;Idempotent(prefix = "order:start", key = "#userId + ':' + #dto.chargerId", ttl = 10)
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * 业务前缀，用于隔离不同接口的 key 空间
     */
    String prefix();

    /**
     * 幂等键的 SpEL 表达式，支持通过 {@code #参数名} 引用方法入参。
     * 表达式结果为空时视为不加锁（不做幂等校验）。
     */
    String key();

    /**
     * 去重窗口(秒)，即 TTL。窗口内重复请求会被直接拒绝
     */
    long ttl() default 10;

    /**
     * 重复提交时的提示语
     */
    String message() default "操作过于频繁，请勿重复提交";
}

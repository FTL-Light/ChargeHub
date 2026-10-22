package com.charging.common;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 缓存 Key 常量管理
 */
public final class CacheKeys {

    private CacheKeys() {}

    /**
     * 充电站详情
     */
    public static String stationDetail(Long id) {
        return "station:detail:" + id;
    }

    /**
     * 充电站详情缓存重建锁(防击穿)：热点 key 失效瞬间只允许一个线程回源
     */
    public static String stationDetailLock(Long id) {
        return "lock:cache:station:" + id;
    }

    /**
     * 公告列表
     */
    public static String noticeList(int current) {
        return "notice:list:" + current;
    }

    /**
     * 公告列表缓存 Key 集合（用于批量删除）
     */
    public static List<String> noticeListKeys(int maxPage) {
        return IntStream.rangeClosed(1, maxPage)
                .mapToObj(CacheKeys::noticeList)
                .collect(Collectors.toList());
    }

    /**
     * 订单统计数据
     */
    public static final String ORDER_STATS = "dashboard:order:stats";
}

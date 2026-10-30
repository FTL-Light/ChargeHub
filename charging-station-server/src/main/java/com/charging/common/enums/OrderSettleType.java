package com.charging.common.enums;

/**
 * 订单结算方式
 *
 * <p>用于区分订单是被用户主动结束、还是被超时机制自动收敛，
 * 既是统计口径（自动结算量/卡单拦截量），也是线上问题回溯的依据。
 */
public enum OrderSettleType {

    /** 用户主动点击结束充电 */
    MANUAL(0, "用户主动"),

    /** 延迟消息触发的超时自动结算 */
    AUTO_TIMEOUT(1, "超时自动"),

    /** MQ 失效后由定时兜底任务结算 */
    COMPENSATION(2, "兜底任务");

    private final int code;
    private final String desc;

    OrderSettleType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}

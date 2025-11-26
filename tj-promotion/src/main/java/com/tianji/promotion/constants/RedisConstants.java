package com.tianji.promotion.constants;

/**
 * Redis相关常量
 */
public interface RedisConstants {
    /**
     * 优惠券兑换码序列号自增key
     * 用于生成兑换码的唯一序列号
     */
    String COUPON_CODE_SERIAL_KEY = "coupon:code:serial";

    /**
     * 优惠券兑换码范围key（ZSet结构）
     * member: 优惠券ID
     * score: 该优惠券对应的兑换码最大序列号
     * 用于快速查找某个优惠券的兑换码范围
     */
    String COUPON_RANGE_KEY = "coupon:code:range";

    /**
     * 优惠券兑换码状态key（BitMap结构）
     * offset: 兑换码的序列号id
     * value: 0-未使用，1-已使用
     */
    String COUPON_CODE_STATUS_KEY = "coupon:code:status";

    String COUPON_CACHE_KEY_PREFIX = "prs:coupon:";

    String USER_COUPON_CACHE_KEY_SUFFIX = ":usr:coupon";

    /**
     * 优惠券库存缓存key
     * 用于缓存优惠券的库存信息,提高查询性能
     */
    String COUPON_STOCK_KEY_PREFIX = "coupon:stock:";

    /**
     * Redis中优惠卷Key
     */
    String USER_COUPON_CACHE_KEY = "prs:user:coupon:";
}

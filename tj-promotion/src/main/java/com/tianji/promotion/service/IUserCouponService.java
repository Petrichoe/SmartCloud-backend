package com.tianji.promotion.service;

import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务类
 * </p>
 *
 * @author kevin
 * @since 2025-11-19
 */
public interface IUserCouponService extends IService<UserCoupon> {

    void getUserCoupon(Long id);


    void checkAndCreateUserCoupon(Coupon coupon, Long userId, Integer serialNum);

    void exchangeCoupon(String code);

    /**
     * 异步领取优惠券(消费MQ消息)
     * @param dto 用户优惠券信息
     */
    void asyncReceiveCoupon(UserCouponDTO dto);

}

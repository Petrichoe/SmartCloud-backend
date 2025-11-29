package com.tianji.promotion.listener;

import com.tianji.common.constants.MqConstants;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 优惠券领取消息监听器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponReceiveListener {

    private final IUserCouponService userCouponService;

    /**
     * 监听优惠券领取消息
     * @param dto 用户优惠券信息
     */
    @RabbitListener(queues = "promotion.coupon.receive.queue")
    public void listenCouponReceiveMessage(UserCouponDTO dto) {
        log.info("接收到优惠券领取消息: userId={}, couponId={}", dto.getUserId(), dto.getCouponId());
        // 调用异步领取逻辑（内部已经处理了异常，不需要在这里捕获）
        userCouponService.asyncReceiveCoupon(dto);
    }
}

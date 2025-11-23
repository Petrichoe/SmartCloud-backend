package com.tianji.promotion.controller;


import com.tianji.promotion.service.IUserCouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 前端控制器
 * </p>
 *
 * @author kevin
 * @since 2025-11-19
 */
@RestController
@RequestMapping("/user-coupons")
@RequiredArgsConstructor
@Api(value = "用户领取优惠券的记录，是真正使用的优惠券信息", tags = "用户领取优惠券信息接口")
public class UserCouponController {

    private final IUserCouponService userCouponService;

    @PostMapping("/{id}/receive")
    @ApiOperation("领取优惠券")
    public void getUserCoupon(@PathVariable Long id){
        userCouponService.getUserCoupon(id);
    }

    @ApiOperation("兑换码兑换优惠券接口")
    @PostMapping("/{code}/exchange")
    public void exchangeCoupon(@PathVariable("code") String code){
        userCouponService.exchangeCoupon(code);
    }

}

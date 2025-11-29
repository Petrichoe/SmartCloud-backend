package com.tianji.promotion.controller;


import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.promotion.service.IDiscountService;
import com.tianji.promotion.service.IUserCouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    private final IDiscountService discountService;



    @ApiOperation("查询我的优惠券可用方案")
    @PostMapping("/available")
    public List<CouponDiscountDTO> findDiscountSolution(@RequestBody List<OrderCourseDTO> orderCourses) {
        return discountService.findDiscountSolution(orderCourses);
    }

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

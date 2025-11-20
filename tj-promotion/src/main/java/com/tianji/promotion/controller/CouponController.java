package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.service.ICouponService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 优惠券的规则信息 前端控制器
 * </p>
 *
 * @author kevin
 * @since 2025-11-10
 */
@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
public class CouponController {
    private final ICouponService couponService;

    @ApiOperation("新增优惠券接口")
    @PostMapping
    public void addCoupon(@RequestBody CouponFormDTO couponFormDTO) {
        couponService.addCoupon(couponFormDTO);
    }

    @ApiOperation("分页查询优惠券接口")
    @GetMapping("/page")
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query){
        return couponService.queryCouponByPage(query);
    }

    @ApiOperation("发放优惠券接口")
    @PutMapping("/{id}/issue")
    public void beginIssue(@RequestBody @Valid CouponIssueFormDTO dto) {
        couponService.beginIssue(dto);
    }

    @ApiOperation("删除优惠卷接口")
    @DeleteMapping("/{id}")
    public void deleteCoupon(@PathVariable Long id) {
        couponService.removeCuoponById(id);
    }

    @ApiOperation("暂停优惠券发放接口")
    @PutMapping("/{id}/pause")
    public void pauseCoupon(@PathVariable Long id) {
        couponService.pauseCoupon(id);
    }



}

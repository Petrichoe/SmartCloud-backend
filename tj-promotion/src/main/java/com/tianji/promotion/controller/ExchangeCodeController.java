package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.query.CodeQuery;
import com.tianji.promotion.domain.vo.ExchangeCodePageVO;
import com.tianji.promotion.service.IExchangeCodeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 兑换码 前端控制器
 * </p>
 *
 * @author kevin
 * @since 2025-11-10
 */
@RestController
@RequestMapping("/codes")
@RequiredArgsConstructor
@Api(tags = "兑换码相关接口")
public class ExchangeCodeController {

    private final IExchangeCodeService codeService;

    @ApiOperation("分页查询兑换码接口")
    @GetMapping("/page")
    public PageDTO<ExchangeCodePageVO> queryExchangeCodeByPage(CodeQuery query){
        return codeService.queryCodePage(query);
    }

}

package com.tianji.promotion.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "兑换码分页数据")
public class ExchangeCodePageVO {
    @ApiModelProperty("兑换码id")
    private Integer id;

    @ApiModelProperty("兑换码")
    private String code;
}

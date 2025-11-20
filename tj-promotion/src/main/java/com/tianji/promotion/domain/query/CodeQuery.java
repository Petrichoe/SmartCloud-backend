package com.tianji.promotion.domain.query;

import com.tianji.common.domain.query.PageQuery;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@ApiModel(description = "兑换码查询参数")
public class CodeQuery extends PageQuery {

    @ApiModelProperty("优惠券id")
    private Long couponId;

    @ApiModelProperty("兑换码状态：1：待兑换，2：已兑换，3：兑换活动已结束")
    private Integer status;
}

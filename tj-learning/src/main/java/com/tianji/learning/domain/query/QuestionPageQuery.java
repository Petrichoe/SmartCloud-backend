package com.tianji.learning.domain.query;

import com.tianji.common.domain.query.PageQuery;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@ApiModel(description = "问题分页查询参数")
@EqualsAndHashCode(callSuper = true)
public class QuestionPageQuery extends PageQuery {
    @ApiModelProperty("课程id")
    private Long courseId;
    @ApiModelProperty("小节id")
    private Long sectionId;
    @ApiModelProperty("是否只查询当前用户创建的问题")
    private Boolean onlyMine;
}

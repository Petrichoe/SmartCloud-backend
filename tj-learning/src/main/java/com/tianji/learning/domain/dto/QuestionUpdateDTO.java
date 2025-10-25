package com.tianji.learning.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;


@Data
@ApiModel(description = "用户修改提问表单实体")
public class QuestionUpdateDTO {

    @ApiModelProperty(value = "问题标题")
    @NotBlank(message = "问题标题不能为空")
    private String title;

    @ApiModelProperty(value = "问题描述")
    private String description;

    @ApiModelProperty(value = "是否匿名")
    @NotNull(message = "是否匿名不能为空")
    private Boolean anonymity;
}

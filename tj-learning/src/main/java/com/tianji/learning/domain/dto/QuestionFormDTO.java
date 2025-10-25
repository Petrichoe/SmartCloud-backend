package com.tianji.learning.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;


@Data
@ApiModel(description = "用户提问表单实体")
public class QuestionFormDTO {

    @ApiModelProperty("课程id")
    @NotNull(message = "课程id不能为空")
    private Long courseId;

    @ApiModelProperty(value = "章id")
    @NotNull(message = "章id不能为空")
    private Long chapterId;

    @ApiModelProperty(value = "节id")
    @NotNull(message = "小节id不能为空")
    private Long sectionId;

    @ApiModelProperty(value = "问题标题")
    @NotBlank(message = "问题标题不能为空")
    private String title;

    @ApiModelProperty(value = "问题描述")
    private String description;

    @ApiModelProperty(value = "是否匿名")
    @NotNull(message = "是否匿名不能为空")
    private Boolean anonymity;
}

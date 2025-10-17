package com.tianji.learning.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDate;

@Data
@ApiModel(description = "用户课程状态信息")
public class UserLessonStatusVO {

    @ApiModelProperty("主键lessonId")
    private Long id;

    @ApiModelProperty("课程id")
    private Long courseId;

    @ApiModelProperty("课程状态，0-未学习，1-学习中，2-已学完，3-已失效")
    private LessonStatus status;

    @ApiModelProperty("已学习章节数")
    private Integer learnedSections;

    @ApiModelProperty("课程购买时间")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate createTime;

    @ApiModelProperty("课程过期时间")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expireTime;

    @ApiModelProperty("学习计划状态，0-没有计划，1-计划进行中")
    private PlanStatus planStatus;
}

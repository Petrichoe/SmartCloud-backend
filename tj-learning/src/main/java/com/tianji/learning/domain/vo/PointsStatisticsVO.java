package com.tianji.learning.domain.vo;

import com.tianji.learning.enums.PointsRecordType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@ApiModel(description = "每日积分实体信息")
@Data
public class PointsStatisticsVO {

    @ApiModelProperty("积分方式：1-课程学习，2-每日签到，3-课程问答， 4-课程笔记，5-课程评价")
    private String type;

    @ApiModelProperty("今日签到获得的积分值")
    private Integer points;

    @ApiModelProperty("每日积分上限")
    private Integer maxPoints;

}

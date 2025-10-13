package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author author
 * @since 2025-10-07
 */
@Api(tags = "我的课表相关接口")
@RestController
@RequestMapping("/lessons")
@RequiredArgsConstructor
public class LearningLessonController {
    private final ILearningLessonService lessonService;

    @ApiOperation("查询我的课表，排序字段 latest_learn_time:学习时间排序，create_time:购买时间排序")
    @GetMapping("/page")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery) {
        return lessonService.queryMyLessons(pageQuery);
    }

    @GetMapping("/now")
    @ApiOperation("查询我正在学习的课程")
    public LearningLessonVO queryMyCurrentLesson() {
        return lessonService.queryMyCurrentLesson();
    }



}

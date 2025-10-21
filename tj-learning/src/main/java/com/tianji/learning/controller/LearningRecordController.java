package com.tianji.learning.controller;


import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.service.ILearningRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学习记录表 前端控制器
 * </p>
 *
 * @author author
 * @since 2025-10-17
 */
@RestController
@RequestMapping("/learning-records")
@Api(tags = "学习记录的相关接口")
@RequiredArgsConstructor
public class LearningRecordController {

    private final ILearningRecordService learningRecordService;

    /**
     * 远程调用接口
     * 查询当前用户指定课程的学习进度
     * @param courseId 课程id
     * @return 课表信息、学习记录及进度信息
     */
    @ApiOperation("查询指定课程的学习记录")
    @GetMapping("/course/{courseId}")
    public LearningLessonDTO queryLearningRecord(@PathVariable("courseId") Long courseId) {
        return learningRecordService.queryLearningRecordByCourse(courseId);
    }

    @ApiOperation("添加学习记录")
    @PostMapping
    public void addLearningRecord(@RequestBody LearningRecordFormDTO learningRecordFormDTO) {
        learningRecordService.addLearningRecord(learningRecordFormDTO);
    }

}

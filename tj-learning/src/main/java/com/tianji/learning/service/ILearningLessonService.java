package com.tianji.learning.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.domain.vo.UserLessonStatusVO;

import java.util.List;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author author
 * @since 2025-10-07
 */
public interface ILearningLessonService extends IService<LearningLesson> {

    void addUserLessons(Long userId, List<Long> courseIds);


    PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery);

    LearningLessonVO queryMyCurrentLesson();

    /**
     * 当用户退款时异步自动删除用户课程
     * @param userId
     * @param courseIds
     */
    void deleteUserLessons(Long userId, List<Long> courseIds);


    void deleteMyLesson(Long courseId);

    /**
     * 检查用户课程是否有效
     * @param courseId
     * @return
     */
    Long isLessonValid(Long courseId);

    /**
     * 查询用户课表中指定课程状态
     * @param courseId
     * @return
     */
    UserLessonStatusVO queryUserLessonStatus(Long courseId);

    /**
     * 创建学习计划
     * @param courseId
     * @param freq
     */
    void createLearningPlan(Long courseId, Integer freq);


    LearningLesson queryByUserAndCourseId(Long userId, Long courseId);

    /**
     * 查询我的学习计划
     * @param pageQuery
     * @return
     */
    LearningPlanPageVO queryMyPlan(PageQuery pageQuery);
}

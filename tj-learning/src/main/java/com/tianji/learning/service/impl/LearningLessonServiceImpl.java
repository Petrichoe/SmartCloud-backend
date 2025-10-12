package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author author
 * @since 2025-10-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    final CourseClient courseClient;

    @Override
    public void addUserLessons(Long userId, List<Long> courseIds) {
        log.debug("开始处理报名，用户: {}, 课程Ids: {}", userId, courseIds);
        // 查询课程信息
        List<CourseSimpleInfoDTO> simpleInfoList = courseClient.getSimpleInfoList(courseIds);
        log.debug("从课程服务查询到课程信息: {}", simpleInfoList);

        if (simpleInfoList == null || simpleInfoList.isEmpty()) {
            log.warn("课程信息查询为空，无法为用户添加课表。用户: {}, 课程Ids: {}", userId, courseIds);
            return;
        }

        //封装po实体类，填充过期时间
        List<LearningLesson> list=new ArrayList<>();
        for (CourseSimpleInfoDTO cinfo : simpleInfoList) {
            LearningLesson lesson = new LearningLesson();
            lesson.setUserId(userId);
            lesson.setCourseId(cinfo.getId());
            if(cinfo.getValidDuration()!= null){
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                lesson.setExpireTime(now.plusMonths(cinfo.getValidDuration()));
            }
            list.add(lesson);
        }
        log.debug("准备批量保存到数据库的课表数量: {}", list.size());
        // 批量保存
        saveBatch(list);
        log.info("成功为用户 {} 添加 {} 门课程到课表。", userId, list.size());
    }
}

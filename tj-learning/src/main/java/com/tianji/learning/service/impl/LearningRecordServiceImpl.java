package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author author
 * @since 2025-10-17
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService lessonService;

    private final CourseClient courseClient;

    private final LearningRecordDelayTaskHandler delayTaskHandler;

    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        Long userId = UserContext.getUser();
        // 2.查询课表
        LearningLesson lesson = lessonService.queryByUserAndCourseId(userId, courseId);
        if (lesson == null) {
            // 用户还未开始学习课程，直接返回
            return new LearningLessonDTO();
        }
        // 3.查询学习记录
        // select * from xx where lesson_id = #{lessonId}
        List<LearningRecord> record = lambdaQuery()
                .eq(LearningRecord::getLessonId, lesson.getId()).list();


        LearningLessonDTO dto = new LearningLessonDTO();
        dto.setId(lesson.getId());
        dto.setLatestSectionId(lesson.getLatestSectionId());
        dto.setRecords(BeanUtils.copyList(record, LearningRecordDTO.class));

        return dto;
    }

    @Transactional
    @Override
    public void addLearningRecord(LearningRecordFormDTO learningRecordFormDTO) {
        Long userId = UserContext.getUser();

        boolean finished = false;//是否完成学习
        //判断是否完成学习
        if (learningRecordFormDTO.getSectionType() == SectionType.VIDEO) {
            //处理视频
            finished=handleVideoRecord(learningRecordFormDTO, userId);
        }else{
            //处理考试
            finished=handleExamRecord(learningRecordFormDTO, userId);
        }
        //数据库优化处理
        if (!finished) {
            // 没有新学完的小节，无需更新课表中的学习进度
            return;
        }

        // 3.处理课表数据
        handleLearningLessonsChanges(learningRecordFormDTO, finished);

    }

    private void handleLearningLessonsChanges(LearningRecordFormDTO learningRecordFormDTO, boolean finished) {
        LearningLesson lesson = lessonService.getById(learningRecordFormDTO.getLessonId());
        if(lesson==null){
            throw new DbException("课程不存在！");
        }
        boolean allLearned = false;
        if(finished){
            // 3.如果有新完成的小节，则需要查询课程数据
            CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
            if (cInfo == null) {
                throw new BizIllegalException("课程不存在，无法更新数据！");
            }
            // 4.比较课程是否全部学完：已学习小节 >= 课程总小节
            allLearned = lesson.getLearnedSections() + 1 >= cInfo.getSectionNum();
        }
        //更新课表数据
        lessonService.lambdaUpdate()
                .set(lesson.getLearnedSections()==0, LearningLesson::getStatus, LessonStatus.LEARNING)
                .set(allLearned, LearningLesson::getStatus, LessonStatus.FINISHED.getValue())
                .set(!finished, LearningLesson::getLatestSectionId, learningRecordFormDTO.getSectionId())
                .set(!finished, LearningLesson::getLatestLearnTime,  learningRecordFormDTO.getCommitTime())
                .setSql(finished, "learned_sections = learned_sections + 1")
                .eq(LearningLesson::getId, lesson.getId())
                .update();


    }

    private boolean handleExamRecord(LearningRecordFormDTO learningRecordFormDTO, Long userId) {

        LearningRecord learningRecord = BeanUtils.copyBean(learningRecordFormDTO, LearningRecord.class);
        learningRecord.setUserId(userId);
        learningRecord.setCreateTime(LocalDateTime.now());
        boolean success = save(learningRecord);
        if (!success){
            throw new DbException("新增学习记录失败！");
        }
        return true;

    }

    private boolean handleVideoRecord(LearningRecordFormDTO learningRecordFormDTO, Long userId) {

        // 1.查询旧的学习记录
        LearningRecord old = queryOldRecord(learningRecordFormDTO.getLessonId(), learningRecordFormDTO.getSectionId());

        //如果没有记录，就新建记录，否则更新记录
        if (old == null) {
            LearningRecord learningRecord = BeanUtils.copyBean(learningRecordFormDTO, LearningRecord.class);
            learningRecord.setUserId(userId);
            boolean success = save(learningRecord);
            if (!success) {
                throw new DbException("新增学习记录失败！");
            }
            return false;
        }

        // 4.存在，则更新
        // 4.1.判断是否是第一次完成
        boolean finished = !old.getFinished() && learningRecordFormDTO.getMoment() * 2 >= learningRecordFormDTO.getDuration();
        if (!finished) {
            LearningRecord record1 = new LearningRecord();
            record1.setLessonId(learningRecordFormDTO.getLessonId());
            record1.setSectionId(learningRecordFormDTO.getSectionId());
            record1.setMoment(learningRecordFormDTO.getMoment());
            record1.setId(old.getId());
            record1.setFinished(old.getFinished());
            delayTaskHandler.addLearningRecordTask(record1);
            return false;
        }
        // 4.2.更新数据
        boolean update = lambdaUpdate()
                .set(LearningRecord::getMoment, learningRecordFormDTO.getMoment())
                .set(finished, LearningRecord::getFinished, true)//finished为true就把getFinished设置为true否则跳过
                .set(finished, LearningRecord::getUpdateTime, learningRecordFormDTO.getCommitTime())
                .eq(LearningRecord::getId, old.getId())
                .update();

        if (!update){
            throw new DbException("更新学习记录失败！");
        }
        // 4.3.清理缓存
        delayTaskHandler.cleanRecordCache(learningRecordFormDTO.getLessonId(),learningRecordFormDTO.getSectionId());
        return true;

    }

    private LearningRecord queryOldRecord(Long lessonId, Long sectionId) {
        // 1.查询缓存
        LearningRecord record = delayTaskHandler.readRecordCache(lessonId, sectionId);
        // 2.如果命中，直接返回
        if (record != null) {
            return record;
        }
        // 3.未命中，查询数据库
        record = lambdaQuery()
                .eq(LearningRecord::getLessonId, lessonId)
                .eq(LearningRecord::getSectionId, sectionId)
                .one();
        // 4.写入缓存
        delayTaskHandler.writeRecordCache(record);
        return record;
    }


}

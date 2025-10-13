package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    private final CatalogueClient catalogueClient;


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

    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        Long userId = UserContext.getUser();
        Page<LearningLesson> page = lambdaQuery().eq(LearningLesson::getUserId, userId)
                                                            .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records=page.getRecords();
        //检查看有没有记录
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }

        // 3.查询课程信息
        Map<Long, CourseSimpleInfoDTO> cMap = queryCourseSimpleInfoList(records);

        List<LearningLessonVO> list=new ArrayList<>();
        for (LearningLesson r : records) {
            // 4.2.拷贝基础属性到vo
            LearningLessonVO vo = BeanUtils.copyBean(r, LearningLessonVO.class);
            // 4.3.获取课程信息，填充到vo
            CourseSimpleInfoDTO cInfo = cMap.get(r.getCourseId());
            vo.setCourseName(cInfo.getName());
            vo.setCourseCoverUrl(cInfo.getCoverUrl());
            vo.setSections(cInfo.getSectionNum());
            list.add(vo);
        }

        PageDTO<LearningLessonVO> pageDTO = PageDTO.of(page, list);
        return pageDTO;

    }

    @Override
    public LearningLessonVO queryMyCurrentLesson() {
        Long userId = UserContext.getUser();
        //根据userid查询出正在学习的课程
        LearningLesson lesson = lambdaQuery().eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING)
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if(lesson== null){
            return null;
        }

        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);

        CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cInfo == null) {
            throw new BadRequestException("课程不存在");
        }
        vo.setCourseName(cInfo.getName());
        vo.setCourseCoverUrl(cInfo.getCoverUrl());
        vo.setSections(cInfo.getSectionNum());
        // 5.统计课表中的课程数量 select count(1) from xxx where user_id = #{userId}
        Integer courseAmount = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .count();
        vo.setCourseAmount(courseAmount);
        // 6.查询小节信息
        List<CataSimpleInfoDTO> cataInfos =
                catalogueClient.batchQueryCatalogue(CollUtils.singletonList(lesson.getLatestSectionId()));
        if (!CollUtils.isEmpty(cataInfos)) {
            CataSimpleInfoDTO cataInfo = cataInfos.get(0);
            vo.setLatestSectionName(cataInfo.getName());
            vo.setLatestSectionIndex(cataInfo.getCIndex());
        }
        return vo;



    }

    private Map<Long, CourseSimpleInfoDTO> queryCourseSimpleInfoList(List<LearningLesson> records) {
        // 3.1.获取课程id
        Set<Long> cIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        // 3.2.查询课程信息
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(cIds);
        if (CollUtils.isEmpty(cInfoList)) {
            // 课程不存在，无法添加
            throw new BadRequestException("课程信息不存在！");
        }
        // 3.3.把课程集合处理成Map，key是courseId，值是course本身
        Map<Long, CourseSimpleInfoDTO> cMap = cInfoList.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        return cMap;
    }
}

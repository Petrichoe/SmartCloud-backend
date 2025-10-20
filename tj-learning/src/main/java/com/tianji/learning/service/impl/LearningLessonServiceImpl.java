package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
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
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.domain.vo.UserLessonStatusVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;

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

    private final LearningRecordMapper recordMapper;


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

    //TODO 当用户退款时异步自动删除用户课程
    @Override
    public void deleteUserLessons(Long userId, List<Long> courseIds) {
        // 1.根据用户id和课程id查询课程
        List<LearningLesson> lessons = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .in(LearningLesson::getCourseId, courseIds)
                .list();
        if (CollUtils.isEmpty(lessons)) {
            return;
        }
        log.debug("查询到用户{}的课程信息{}条", userId, lessons.size());

        // 2.获取课表id
        List<Long> lessonIds = lessons.stream().map(LearningLesson::getId).collect(Collectors.toList());


        // 4.根据id删除课表
        removeByIds(lessonIds);
        log.debug("删除用户{}的课表{}条", userId, lessonIds.size());
    }



    @Override
    public void deleteMyLesson(Long courseId) {
        // 1.获取当前登录人
        Long userId = UserContext.getUser();
        // 2.根据用户id和课程id查询课程
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            throw new BadRequestException("课程不存在");
        }

        // 3.判断课程是否过期
        /*LocalDateTime expireTime = lesson.getExpireTime();
        if (expireTime == null || expireTime.isAfter(LocalDateTime.now())) {
            throw new BadRequestException("课程未过期，无法删除");
        }*/

        if(lesson.getStatus() == LessonStatus.EXPIRED){
            // 4.删除课程
            deleteUserLessons(userId, CollUtils.singletonList(courseId));
        }

    }

    @Override
    public Long isLessonValid(Long courseId) {
        // 1.获取当前登录人
        Long userId = UserContext.getUser();
        // 2.根据用户id和课程id查询课程
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson==null){
            throw new BadRequestException("课程不存在");
        }
        if (lesson.getStatus() == LessonStatus.EXPIRED){
            throw new BadRequestException("课程已过期");
        }else {
            return lesson.getId();
        }


    }

    @Override
    public UserLessonStatusVO queryUserLessonStatus(Long courseId) {
        Long userId = UserContext.getUser();
        LearningLesson lesson = lambdaQuery().eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            throw new BadRequestException("课程不存在");
        }
        UserLessonStatusVO vo = new UserLessonStatusVO();
        vo.setId(lesson.getId());
        vo.setCourseId(lesson.getCourseId());
        vo.setStatus(lesson.getStatus());
        vo.setLearnedSections(lesson.getLearnedSections());
        vo.setCreateTime(lesson.getCreateTime().toLocalDate());
        if (lesson.getExpireTime() != null) {
            vo.setExpireTime(lesson.getExpireTime().toLocalDate());
        }
        vo.setPlanStatus(lesson.getPlanStatus());
        return vo;
    }

    @Override
    public void createLearningPlan(Long courseId, Integer freq) {
        Long userId = UserContext.getUser();
        LearningLesson lessons = lambdaQuery().eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lessons == null) {
            throw new BadRequestException("课程不存在");
        }
        LearningLesson lesson = new LearningLesson();
        lesson.setId(lessons.getId());
        lesson.setWeekFreq(freq);
        if(lessons.getPlanStatus()== PlanStatus.NO_PLAN ){
             lesson.setPlanStatus(PlanStatus.PLAN_RUNNING);
        }
        updateById(lesson);
    }


    /**
     * 定时任务，检查课表状态
     */
    @Scheduled(cron = "0 0 12-18 * * ?")
    public void checkLessonStatus(){
        // 1.查询所有已过期但状态未更新的课程
        List<LearningLesson> lessons = lambdaQuery()
                .lt(LearningLesson::getExpireTime, LocalDateTime.now())
                .ne(LearningLesson::getStatus, LessonStatus.EXPIRED)
                .list();
        if (CollUtils.isEmpty(lessons)) {
            return;
        }
        // 2.更新状态
        for (LearningLesson lesson : lessons) {
            lesson.setStatus(LessonStatus.EXPIRED);
        }
        // 3.批量更新
        updateBatchById(lessons);
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

    @Override
    public LearningLesson queryByUserAndCourseId(Long userId, Long courseId) {

        return getOne(buildUserIdAndCourseIdWrapper(userId, courseId));//可复用设计，下次也可以 return remove(...)
    }

    @Override
    public LearningPlanPageVO queryMyPlan(PageQuery pageQuery) {
        LearningPlanPageVO pageVO = new LearningPlanPageVO();
        Long userId = UserContext.getUser();
        // 获取本周起始时间
        LocalDate now = LocalDate.now();
        LocalDateTime begin = DateUtils.getWeekBeginTime(now);
        LocalDateTime end = DateUtils.getWeekEndTime(now);

        // 查询本周总的已学习小节数量
        Integer weekFinished = recordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .gt(LearningRecord::getCreateTime, begin)
                .lt(LearningRecord::getCreateTime, end)
        );
        pageVO.setWeekFinished(weekFinished);

        // 查询本周总的计划学习数量
        // 1. 构建查询条件，查询出所有运行中的学习计划
        LambdaQueryWrapper<LearningLesson> queryWrapper = new LambdaQueryWrapper<LearningLesson>()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING);
        // 2. 只查询 week_freq 字段
        queryWrapper.select(LearningLesson::getWeekFreq);
        // 3. 利用 listObjs 获取 week_freq 列表并求和
        List<Object> objects = listObjs(queryWrapper);
        Integer weekTotalPlan = objects.stream().mapToInt(o -> (int) o).sum();
        pageVO.setWeekTotalPlan(weekTotalPlan);

        // 1. 分页查询我的学习计划
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .page(pageQuery.toMpPage("create_time", false));
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return pageVO.pageInfo(PageDTO.empty(page));
        }

        // 2. 查询课程信息
        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cInfoList)) {
            return pageVO.pageInfo(PageDTO.empty(page));
        }
        Map<Long, CourseSimpleInfoDTO> courseMap = cInfoList.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));

        // 3. 封装VO
        List<LearningPlanVO> voList = new ArrayList<>();
        for (LearningLesson r : records) {
            LearningPlanVO vo = BeanUtils.copyBean(r, LearningPlanVO.class);
            CourseSimpleInfoDTO cInfo = courseMap.get(r.getCourseId());
            if (cInfo != null) {
                vo.setCourseName(cInfo.getName());
                vo.setSections(cInfo.getSectionNum());
            }
            voList.add(vo);
        }

        // 4. 封装分页结果
        pageVO.pageInfo(page.getTotal(), page.getPages(), voList);

        return pageVO;
    }

    private LambdaQueryWrapper<LearningLesson> buildUserIdAndCourseIdWrapper(Long userId, Long courseId) {
        LambdaQueryWrapper<LearningLesson> queryWrapper = new QueryWrapper<LearningLesson>()
                .lambda()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId);
        return queryWrapper;
    }




}

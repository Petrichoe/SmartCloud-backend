package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.ForbiddenException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.dto.QuestionUpdateDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author author
 * @since 2025-10-22
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {
    private final InteractionReplyMapper replyMapper;

    private final UserClient userClient;

    private final CourseClient courseClient;

    private final SearchClient searchClient;

    private final CatalogueClient catalogueClient;

    private final CategoryCache categoryCache;

    @Override
    public void saveQuestion(QuestionFormDTO questionFormDTO) {
        Long useId = UserContext.getUser();
        InteractionQuestion question = BeanUtils.copyBean(questionFormDTO, InteractionQuestion.class);
        question.setUserId(useId);
        save(question);
    }

    @Override
    public void updateQuestion(QuestionUpdateDTO questionUpdateDTO, Long id) {
        // 1. 根据ID从数据库查询原始数据
        InteractionQuestion question = this.getById(id);
        if (question == null) {
            // 如果问题不存在，直接抛出异常
            throw new BadRequestException("问题不存在");
        }

        // 2. 权限校验：检查当前用户是否是问题的提问者
        Long currentUserId = UserContext.getUser();
        if (!question.getUserId().equals(currentUserId)) {
            // 如果不是，抛出异常
            throw new ForbiddenException("无权修改他人的问题");
        }

        // 3. 将DTO中的数据更新到查询出的实体上
        question.setTitle(questionUpdateDTO.getTitle());
        question.setDescription(questionUpdateDTO.getDescription());
        question.setAnonymity(questionUpdateDTO.getAnonymity());

        // 4. 执行更新
        boolean success = this.updateById(question);
        if (!success) {
            throw new RuntimeException("更新失败，请稍后重试");
        }
    }

    @Override
    public PageDTO<QuestionVO> queryQuestionByPage(QuestionPageQuery query) {
        Long courseId = query.getCourseId();
        Long sectionId = query.getSectionId();
        if (courseId == null && sectionId == null) {
            throw new BadRequestException("课程id和小节id不能都为空");
        }
        // 2.分页查询
        Page<InteractionQuestion> page = lambdaQuery()
                .select(InteractionQuestion.class, info -> !info.getProperty().equals("description"))
                .eq(query.getOnlyMine(), InteractionQuestion::getUserId, UserContext.getUser())
                .eq(courseId != null, InteractionQuestion::getCourseId, courseId)
                .eq(sectionId != null, InteractionQuestion::getSectionId, sectionId)
                .eq(InteractionQuestion::getHidden, false)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 3.根据id查询提问者和最近一次回答的信息
        Set<Long> userIds = new HashSet<>();
        Set<Long> answerIds = new HashSet<>();
        // 3.1.得到问题当中的提问者id和最近一次回答的id
        for (InteractionQuestion q : records) {
            if(!q.getAnonymity()) { // 只查询非匿名的问题
                userIds.add(q.getUserId());
            }
            answerIds.add(q.getLatestAnswerId());
        }
        // 3.2.根据id查询最近一次回答
        answerIds.remove(null);
        Map<Long, InteractionReply> replyMap = new HashMap<>(answerIds.size());
        if(CollUtils.isNotEmpty(answerIds)) {
            List<InteractionReply> replies = replyMapper.selectBatchIds(answerIds);
            for (InteractionReply reply : replies) {
                replyMap.put(reply.getId(), reply);
                if(!reply.getAnonymity()){ // 匿名用户不做查询
                    userIds.add(reply.getUserId());
                }
            }
        }

        // 3.3.根据id查询用户信息（提问者）
        userIds.remove(null);
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if(CollUtils.isNotEmpty(userIds)) {
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            userMap = users.stream()
                    .collect(Collectors.toMap(UserDTO::getId, u -> u));
        }

        // 4.封装VO
        List<QuestionVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion r : records) {
            // 4.1.将PO转为VO
            QuestionVO vo = BeanUtils.copyBean(r, QuestionVO.class);
            vo.setUserId(null);
            voList.add(vo);
            // 4.2.封装提问者信息
            if(!r.getAnonymity()){
                UserDTO userDTO = userMap.get(r.getUserId());
                if (userDTO != null) {
                    vo.setUserId(userDTO.getId());
                    vo.setUserName(userDTO.getName());
                    vo.setUserIcon(userDTO.getIcon());
                }
            }

            // 4.3.封装最近一次回答的信息
            InteractionReply reply = replyMap.get(r.getLatestAnswerId());
            if (reply != null) {
                vo.setLatestReplyContent(reply.getContent());
                if(!reply.getAnonymity()){// 匿名用户直接忽略
                    UserDTO user = userMap.get(reply.getUserId());
                    vo.setLatestReplyUser(user.getName());
                }

            }
        }

        return PageDTO.of(page, voList);
    }

    @Override
    public QuestionVO queryQuestionById(Long id) {
        // 1.根据id查询问题
        InteractionQuestion question = this.getById(id);
        if (question == null) {
            throw new BadRequestException("问题不存在");
        }

        // 2.检查问题是否被隐藏
        if (question.getHidden()) {
            throw new BadRequestException("问题已被隐藏");
        }

        // 3.权限校验：只能查询自己的问题
        Long currentUserId = UserContext.getUser();
        if (!question.getUserId().equals(currentUserId)) {
            throw new ForbiddenException("无权查看他人的问题");
        }

        // 3.查询提问者信息
        UserDTO user = null;
        if(!question.getAnonymity()){
            user = userClient.queryUserById(question.getUserId());
        }

        // 4.封装VO
        QuestionVO vo = BeanUtils.copyBean(question, QuestionVO.class);
        if (user != null) {
            vo.setUserName(user.getName());
            vo.setUserIcon(user.getIcon());

        }
        return vo;
    }

    @Override
    public void deleteQuestion(Long id) {
        // 1.查询问题是否存在
        InteractionQuestion question = getById(id);
        if (question == null) {
            throw new BadRequestException("问题不存在");
        }
        // 2.判断是否是当前用户提问的
        Long userId = UserContext.getUser();
        if(!userId.equals(question.getUserId())){
            // 3.如果不是则报错
            throw new ForbiddenException("无权删除他人的问题");
        }
        // 4.如果是则删除问题
        removeById(id);
        // 5.然后删除问题下的回答及评论
        replyMapper.delete(new LambdaQueryWrapper<InteractionReply>().eq(InteractionReply::getQuestionId, id));
    }

    @Override
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query) {
        //根据课程名称查询课程id
        List<Long> courseIds=null;
        if (StringUtils.isNotBlank(query.getCourseName())){
            courseIds = searchClient.queryCoursesIdByName(query.getCourseName());
            if (CollUtils.isEmpty(courseIds)){
                return null;
            }
        }
        //分页查询
        Integer status = query.getStatus();
        LocalDateTime beginTime = query.getBeginTime();
        LocalDateTime endTime = query.getEndTime();
        Page<InteractionQuestion> page = lambdaQuery().in(courseIds != null, InteractionQuestion::getCourseId, courseIds)
                .eq(status != null, InteractionQuestion::getStatus, status)
                .gt(beginTime != null, InteractionQuestion::getCreateTime, beginTime)
                .lt(endTime != null, InteractionQuestion::getCreateTime, endTime)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }

        //为什么用Set来存储呢:去重
        Set<Long> userIds = new HashSet<>();
        Set<Long> cIds = new HashSet<>();
        Set<Long> chapterIds = new HashSet<>();
        //获取各种id的集合
        for (InteractionQuestion r : records){
            userIds.add(r.getUserId());
            cIds.add(r.getCourseId());
            chapterIds.add(r.getChapterId());
            chapterIds.add(r.getSectionId());
        }

        List<UserDTO> users = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userMap=new HashMap<>(users.size()); //使用Map可以在随后瞬时查找
        if (CollUtils.isNotEmpty(users)){
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }

        List<CourseSimpleInfoDTO> courses = courseClient.getSimpleInfoList(cIds);
        Map<Long, CourseSimpleInfoDTO> courseMap=new HashMap<>(courses.size());
        if (CollUtils.isNotEmpty(courses)){
            courseMap = courses.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        }

        List<CataSimpleInfoDTO> catas = catalogueClient.batchQueryCatalogue(chapterIds);
        Map<Long, String> cataMap=new HashMap<>(catas.size());
        if (CollUtils.isNotEmpty(catas)){
            cataMap = catas.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId,CataSimpleInfoDTO::getName ));

        }

        List<QuestionAdminVO> voList=new ArrayList<>(records.size());
        for (InteractionQuestion r : records){
            QuestionAdminVO vo = BeanUtils.copyBean(r, QuestionAdminVO.class);
            voList.add(vo);

            UserDTO user = userMap.get(r.getUserId());
            if (user != null) {
                vo.setUserName(user.getName());
            }

            CourseSimpleInfoDTO course = courseMap.get(r.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getName());//理解下面这个
                vo.setCategoryName(categoryCache.getCategoryNames(course.getCategoryIds()));

            }

            vo.setChapterName(cataMap.getOrDefault(r.getChapterId(), ""));
            vo.setChapterName(cataMap.getOrDefault(r.getSectionId(), ""));
        }
        return PageDTO.of(page, voList);
    }
}
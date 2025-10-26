package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author author
 * @since 2025-10-22
 */
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final IInteractionQuestionService questionService;
    private final UserClient userClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addReplys(ReplyDTO replyDTO) {
        // 1. 获取当前登录用户ID
        Long userId = UserContext.getUser();

        // 2. 判断是回答还是评论
        if (replyDTO.getAnswerId() == null) {
            // 这是一个回答（直接回答问题）
            saveAnswer(replyDTO, userId);
        } else {
            // 这是一个评论（回复某个回答）
            saveComment(replyDTO, userId);
        }
    }



    /**
     * 保存回答并更新问题表
     * @param replyDTO 回答DTO
     * @param userId 当前用户ID
     */
    private void saveAnswer(ReplyDTO replyDTO, Long userId) {
        // 1. 校验 questionId 是否为空
        Long questionId = replyDTO.getQuestionId();
        if (questionId == null) {
            throw new BadRequestException("问题ID不能为空");
        }

        // 2. 查询问题是否存在
        InteractionQuestion oldQuestion = questionService.getById(questionId);
        if (oldQuestion == null) {
            throw new BadRequestException("问题不存在，问题ID：" + questionId);
        }

        // 3. 构建回答对象
        InteractionReply reply = BeanUtils.copyBean(replyDTO, InteractionReply.class);
        reply.setUserId(userId);

        // 4. 保存回答
        this.save(reply);

        // 5. 更新问题表
        InteractionQuestion question = new InteractionQuestion();
        question.setId(questionId);
        // 5.1 修改问题表最近一个回答的id
        question.setLatestAnswerId(reply.getId());

        // 5.2 累加回答次数
        Integer answerTimes = oldQuestion.getAnswerTimes();
        question.setAnswerTimes(answerTimes == null ? 1 : answerTimes + 1);

        // 5.3 校验是否学生提交
        if (replyDTO.getIsStudent() != null && replyDTO.getIsStudent()) {
            // 学生提交，标记问题状态为未查看
            question.setStatus(QuestionStatus.UN_CHECK);
        }

        // 5.4 更新问题
        questionService.updateById(question);
    }

    /**
     * 保存评论并更新回答表
     * @param replyDTO 评论DTO
     * @param userId 当前用户ID
     */
    private void saveComment(ReplyDTO replyDTO, Long userId) {
        // 1. 先查询被评论的回答是否存在
        InteractionReply targetReply = this.getById(replyDTO.getAnswerId());
        if (targetReply == null) {
            throw new BadRequestException("回答不存在");
        }

        // 2. 构建评论对象
        InteractionReply reply = BeanUtils.copyBean(replyDTO, InteractionReply.class);
        reply.setUserId(userId);

        // 3. 保存评论
        this.save(reply);

        // 4. 累加被评论的回答的评论次数
        InteractionReply updateReply = new InteractionReply();
        updateReply.setId(replyDTO.getAnswerId());
        Integer replyTimes = targetReply.getReplyTimes();
        updateReply.setReplyTimes(replyTimes == null ? 1 : replyTimes + 1);
        this.updateById(updateReply);

        // 5. 校验是否学生提交
        if (replyDTO.getIsStudent() != null && replyDTO.getIsStudent()) {
            // 学生提交评论，也需要将对应的问题标记为未查看
            InteractionQuestion question = new InteractionQuestion();
            question.setId(replyDTO.getQuestionId());
            question.setStatus(QuestionStatus.UN_CHECK);
            questionService.updateById(question);
        }
    }

    @Override
    public PageDTO<ReplyVO> queryReplysByPage(ReplyPageQuery query) {
        // 1. 参数校验：questionId 和 answerId 至少要有一个
        if (query.getQuestionId() == null && query.getAnswerId() == null) {
            throw new BadRequestException("问题id和回答id不能都为空");
        }


        // 2. 分页查询回答或评论
        //目的：从 interaction_reply 表中获取原始的回复或评论数据。
        Page<InteractionReply> page = lambdaQuery()
                .eq(query.getQuestionId() != null, InteractionReply::getQuestionId, query.getQuestionId())
                .eq(query.getAnswerId() != null, InteractionReply::getAnswerId, query.getAnswerId())
                .eq(InteractionReply::getHidden, false) // 不查询被隐藏的
                .page(query.toMpPageDefaultSortByCreateTimeDesc());

        List<InteractionReply> records = page.getRecords();//从分页结果中获取查询到的数据列表
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

//3与4是对于业务逻辑效率的优化,先把需要的ID放到集合中，然后通过一次网络调用查询全部用户信息

        // 3. 收集需要查询的用户ID和目标用户ID
        Set<Long> userIds = new HashSet<>();
        Set<Long> targetUserIds = new HashSet<>();
        for (InteractionReply reply : records) {
            // 非匿名用户才查询用户信息
            if (!reply.getAnonymity()) {
                userIds.add(reply.getUserId());
            }
            // 收集目标用户ID（评论的目标用户）
            if (reply.getTargetUserId() != null) {
                targetUserIds.add(reply.getTargetUserId());
            }
        }

        // 4. 批量查询用户信息
        userIds.addAll(targetUserIds);
        userIds.remove(null);
        Map<Long, UserDTO> userMap = new HashMap<>();
        if (CollUtils.isNotEmpty(userIds)) {
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            if (CollUtils.isNotEmpty(users)) {
                userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
            }
        }

        // 5. 封装VO
        List<ReplyVO> voList = new ArrayList<>(records.size());
        for (InteractionReply reply : records) {
            ReplyVO vo = BeanUtils.copyBean(reply, ReplyVO.class);

            // 5.1 封装回答者/评论者信息
            if (!reply.getAnonymity()) {
                UserDTO user = userMap.get(reply.getUserId());
                if (user != null) {
                    vo.setUserId(user.getId());
                    vo.setUserName(user.getName());
                    vo.setUserIcon(user.getIcon());
                    vo.setUserType(user.getType());
                }
            }

            // 5.2 封装目标用户名字（评论场景）
            if (reply.getTargetUserId() != null) {
                UserDTO targetUser = userMap.get(reply.getTargetUserId());
                if (targetUser != null) {
                    vo.setTargetUserName(targetUser.getName());
                }
            }

            voList.add(vo);
        }

        return PageDTO.of(page, voList);
    }
}

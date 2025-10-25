package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.dto.QuestionUpdateDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;

/**
 * <p>
 * 互动提问的问题表 服务类
 * </p>
 *
 * @author author
 * @since 2025-10-22
 */
public interface IInteractionQuestionService extends IService<InteractionQuestion> {
    /**
     * 新增问题
     * @param questionFormDTO
     */
    void saveQuestion(QuestionFormDTO questionFormDTO);


    void updateQuestion(QuestionUpdateDTO questionUpdateDTO, Long id);

    /**
     * 分页查询问题
     * @param query
     * @return
     */
    PageDTO<QuestionVO> queryQuestionByPage(QuestionPageQuery query);

    /**
     * 根据id查询问题详情
     * @param id 问题id
     * @return 问题详情
     */
    QuestionVO queryQuestionById(Long id);

    /**
     * 删除问题
     * @param id
     */
    void deleteQuestion(Long id);
}

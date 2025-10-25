package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.dto.QuestionUpdateDTO;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author author
 * @since 2025-10-22
 */
@RestController
@RequestMapping("/questions")
@AllArgsConstructor
@Api(tags = "我的问题相关接口")
public class InteractionQuestionController {

    private final IInteractionQuestionService questionService;
    @PostMapping
    @ApiOperation("新增提问")
    public void saveQuestion(@Valid @RequestBody QuestionFormDTO questionFormDTO) {
        questionService.saveQuestion(questionFormDTO);
    }

    @PutMapping("/{id}")
    @ApiOperation("修改提问")
    public void updateQuestion(@Valid @RequestBody QuestionUpdateDTO questionUpdateDTO, @PathVariable("id") Long id) {
        questionService.updateQuestion(questionUpdateDTO, id);
    }

    @GetMapping("/{id}")
    @ApiOperation("根据id查询问题详情")
    public QuestionVO queryQuestionById(@PathVariable("id") Long id) {
        return questionService.queryQuestionById(id);
    }

    @ApiOperation("分页查询互动问题")
    @GetMapping("page")
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        return questionService.queryQuestionByPage(query);
    }

    @ApiOperation("删除我的问题")
    @DeleteMapping("/{id}")
    public void deleteQuestion(@PathVariable("id") Long id) {
        questionService.deleteQuestion(id);
    }

}

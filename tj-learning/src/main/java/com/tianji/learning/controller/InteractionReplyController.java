package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动问题的回答或评论 前端控制器
 * </p>
 *
 * @author author
 * @since 2025-10-22
 */
@Api(tags = "互动问题回答或评论")
@RestController
@RequestMapping("/replies")
@RequiredArgsConstructor
public class InteractionReplyController {

    private final IInteractionReplyService replyService;

    @ApiOperation("新增回答或评论")
    @PostMapping
    public void addReply(@RequestBody @Validated ReplyDTO replyDTO){
        replyService.addReplys(replyDTO);
    }

    @GetMapping("/page")
    @ApiOperation("分页查询互动回答或评论")
    public PageDTO<ReplyVO> queryReplys(ReplyPageQuery query){
        return replyService.queryReplysByPage(query);
    }

}

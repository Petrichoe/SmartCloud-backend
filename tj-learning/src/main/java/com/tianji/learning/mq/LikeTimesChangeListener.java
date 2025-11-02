package com.tianji.learning.mq;

import com.tianji.api.dto.remark.LikedTimesDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.QA_LIKED_TIMES_KEY;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeTimesChangeListener {

    private final IInteractionReplyService replyService;

    /**
     * 监听点赞数变更消息 - 批量处理
     * @param dtoList 点赞数变更DTO列表
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "qa.liked.times.queue", durable = "true"),
            exchange = @Exchange(name = LIKE_RECORD_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = QA_LIKED_TIMES_KEY
    ))
    public void listenReplyLikedTimesChange(List<LikedTimesDTO> dtoList){
        if (CollUtils.isEmpty(dtoList)) {
            return;
        }

        log.info("监听到 {} 条点赞数变更消息", dtoList.size());

        // 构建批量更新的数据列表
        List<InteractionReply> replyList = new ArrayList<>(dtoList.size());
        for (LikedTimesDTO dto : dtoList) {
            log.debug("处理回答或评论{}的点赞数变更:{}", dto.getBizId(), dto.getLikedTimes());
            InteractionReply r = new InteractionReply();
            r.setId(dto.getBizId());
            r.setLikedTimes(dto.getLikedTimes());
            replyList.add(r);
        }

        // 批量更新数据库
        replyService.updateBatchById(replyList);

        log.info("批量更新 {} 条点赞数据完成", replyList.size());
    }
}


package com.tianji.remark.handler;

import com.tianji.api.dto.remark.LikedTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.service.ILikedRecordService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE;

/**
 * 点赞相关定时任务处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikedTimesJobHandler {

    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;
    private final ILikedRecordService likedRecordService;

    /**
     * 定时任务：从Redis中读取点赞数据，持久化到数据库并发送到MQ
     * 该任务会处理所有业务类型的点赞数据
     */
    @XxlJob("syncLikedTimesJob")
    public void syncLikedTimesJob() {
        log.info("开始执行点赞数据同步定时任务...");

        // 1. 扫描所有业务类型的点赞统计key
        // 使用 keys 命令扫描所有符合模式的key（生产环境建议使用 scan 命令）
        Set<String> keys = redisTemplate.keys(RedisConstants.LIKES_TIMES_KEY_PREFIX + "*");

        if (CollUtils.isEmpty(keys)) {
            log.info("没有需要同步的点赞数据");
            return;
        }

        log.info("找到 {} 个业务类型需要同步点赞数据", keys.size());

        // 2. 遍历每个业务类型的key
        for (String key : keys) {
            try {
                // 2.1 从key中提取业务类型
                String bizType = key.replace(RedisConstants.LIKES_TIMES_KEY_PREFIX, "");

                // 2.2 从Redis ZSet中读取所有数据
                // ZSet中 member是bizId，score是点赞总数
                Set<ZSetOperations.TypedTuple<String>> typedTuples =
                    redisTemplate.opsForZSet().rangeWithScores(key, 0, -1);

                if (CollUtils.isEmpty(typedTuples)) {
                    log.debug("业务类型 {} 没有点赞数据", bizType);
                    continue;
                }

                log.info("业务类型 {} 有 {} 条业务的点赞数据需要同步", bizType, typedTuples.size());

                // 2.3 准备要持久化的点赞记录列表和要发送的点赞总数列表
                List<LikedRecord> likedRecordList = new ArrayList<>();
                List<LikedTimesDTO> likedTimesDTOList = new ArrayList<>(typedTuples.size());

                // 2.4 遍历每个业务，读取其点赞用户列表
                for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
                    String bizId = tuple.getValue();
                    Double score = tuple.getScore();

                    if (bizId == null || score == null) {
                        continue;
                    }

                    // 2.4.1 从Redis Set中读取该业务的所有点赞用户
                    String likesSetKey = RedisConstants.LIKES_BIZ_KEY_PREFIX + bizId;
                    Set<String> userIds = redisTemplate.opsForSet().members(likesSetKey);

                    if (CollUtils.isNotEmpty(userIds)) {
                        // 构造点赞记录列表
                        for (String userId : userIds) {
                            LikedRecord record = new LikedRecord();
                            record.setUserId(Long.valueOf(userId));
                            record.setBizId(Long.valueOf(bizId));
                            record.setBizType(bizType);
                            likedRecordList.add(record);
                        }
                    }

                    // 2.4.2 构造点赞总数DTO
                    LikedTimesDTO dto = LikedTimesDTO.of(Long.valueOf(bizId), score.intValue());
                    likedTimesDTOList.add(dto);
                }

                // 2.5 批量持久化点赞记录到数据库
                if (CollUtils.isNotEmpty(likedRecordList)) {
                    likedRecordService.saveBatch(likedRecordList);
                    log.info("业务类型 {} 的 {} 条点赞记录已持久化到数据库", bizType, likedRecordList.size());
                }

                // 2.6 批量发送点赞总数到MQ
                if (CollUtils.isNotEmpty(likedTimesDTOList)) {
                    // 根据业务类型生成对应的routing key
                    String routingKey = StringUtils.format(LIKED_TIMES_KEY_TEMPLATE, bizType);

                    // 批量发送消息到MQ
                    mqHelper.send(LIKE_RECORD_EXCHANGE, routingKey, likedTimesDTOList);

                    log.info("业务类型 {} 的 {} 条点赞总数已批量发送到MQ", bizType, likedTimesDTOList.size());
                }

                // 2.7 清理Redis缓存数据
                // 删除点赞总数的ZSet key
                redisTemplate.delete(key);

                // 删除每个业务的点赞用户Set key
                for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
                    String bizId = tuple.getValue();
                    if (bizId != null) {
                        String likesSetKey = RedisConstants.LIKES_BIZ_KEY_PREFIX + bizId;
                        redisTemplate.delete(likesSetKey);
                    }
                }

                log.info("业务类型 {} 的Redis缓存数据已清理", bizType);

            } catch (Exception e) {
                log.error("处理业务类型 {} 的点赞数据时发生异常", key, e);
                // 发生异常不影响其他业务类型的处理，继续处理下一个
            }
        }

        log.info("点赞数据同步定时任务执行完成");
    }
}

package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constans.RedisConstants;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author author
 * @since 2025-10-30
 */
@Service
@RequiredArgsConstructor
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

    private final StringRedisTemplate redisTemplate;

    /**
     * 异步处理添加积分
     * @param userId
     * @param points
     * @param type
     */
    @Override
    public void addPointsRecord(Long userId, int points, PointsRecordType type) {
        int maxPoints = type.getMaxPoints();
        LocalDateTime now = LocalDateTime.now();
        int realPoints = points;
        //判断积分是否有上限
        if (maxPoints > 0){
            //如果有上限就去查询今日已得的积分
            LocalDateTime begin= DateUtils.getDayStartTime( now);
            LocalDateTime end = DateUtils.getDayEndTime(now);
            int currentPoints = queryUserPointsByTypeAndDate(userId, type, begin, end);

            //判断积分是否超出上限
            if(currentPoints>=maxPoints){
                return;
            }

            // 2.4.没超过，保存积分记录
            if(currentPoints + points > maxPoints){
                realPoints = maxPoints - currentPoints;
            }
        }
        //如果没上限就保存积分记录
        PointsRecord p = new PointsRecord();
        p.setPoints(realPoints);
        p.setUserId(userId);
        p.setType(type);
        save(p);

        String key= RedisConstants.POINTS_BOARD_KEY_PREFIX+DateUtils.POINTS_BOARD_SUFFIX_FORMATTER.format(now);
        redisTemplate.opsForZSet().incrementScore(key, userId.toString(), realPoints);
    }

    private int queryUserPointsByTypeAndDate(Long userId, PointsRecordType type, LocalDateTime begin, LocalDateTime end) {
            // 1.查询条件
            QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
            wrapper.lambda()
                    .eq(PointsRecord::getUserId, userId)
                    .eq(type != null, PointsRecord::getType, type)
                    .between(begin != null && end != null, PointsRecord::getCreateTime, begin, end);
            // 2.调用mapper，查询结果
            Integer points = getBaseMapper().queryUserPointsByTypeAndDate(wrapper);
            // 3.判断并返回
            return points == null ? 0 : points;
    }

    @Override
    public List<PointsStatisticsVO> querymyPointToday() {
        Long userId = UserContext.getUser();

        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime begin = DateUtils.getDayStartTime(now);
        LocalDateTime end = DateUtils.getDayEndTime(now);

        LambdaQueryWrapper<PointsRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PointsRecord::getUserId, userId)
                .between(PointsRecord::getCreateTime, begin, end);

        List<PointsRecord> list = getBaseMapper().queryUserPointsByDate(wrapper);

        return list.stream().map(p -> {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            vo.setType(p.getType().getDesc());
            vo.setPoints(p.getPoints());
            vo.setMaxPoints(p.getType().getMaxPoints());
            return vo;
        }).collect(Collectors.toList());
    }
}

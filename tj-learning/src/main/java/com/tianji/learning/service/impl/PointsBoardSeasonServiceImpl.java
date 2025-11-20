package com.tianji.learning.service.impl;

import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.mapper.PointsBoardSeasonMapper;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author author
 * @since 2025-10-30
 */
@Slf4j
@Service
public class PointsBoardSeasonServiceImpl extends ServiceImpl<PointsBoardSeasonMapper, PointsBoardSeason> implements IPointsBoardSeasonService {

    @Override
    public List<PointsBoardSeason> queryBoardSeasons() {
        // 查询所有赛季，按id倒序排列（最新的赛季在前）
        return lambdaQuery()
                .orderByDesc(PointsBoardSeason::getId)
                .list();
    }

    @Override
    public Integer querySeasonByTime(LocalDateTime time) {
        Optional<PointsBoardSeason> optional = lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)
                .ge(PointsBoardSeason::getEndTime, time)
                .oneOpt();
        return optional.map(PointsBoardSeason::getId).orElse(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer createSeasonByTime(LocalDateTime time) {
        // 1. 先查询是否已存在赛季
        Integer existSeasonId = querySeasonByTime(time);
        if (existSeasonId != null) {
            log.info("赛季已存在，赛季ID: {}", existSeasonId);
            return existSeasonId;
        }

        // 2. 计算赛季的开始和结束时间（按月份）
        LocalDate beginTime = LocalDate.of(time.getYear(), time.getMonth(), 1);
        LocalDate endTime = beginTime.plusMonths(1).minusDays(1);

        // 3. 生成赛季名称（查询当前最大的赛季ID，+1）
        Integer maxSeasonId = lambdaQuery()
                .orderByDesc(PointsBoardSeason::getId)
                .last("LIMIT 1")
                .oneOpt()
                .map(PointsBoardSeason::getId)
                .orElse(0);

        // 4. 创建赛季
        PointsBoardSeason season = new PointsBoardSeason();
        season.setName("第" + (maxSeasonId + 1) + "赛季");
        season.setBeginTime(beginTime);
        season.setEndTime(endTime);

        // 5. 保存到数据库
        save(season);

        log.info("自动创建赛季成功，赛季ID: {}, 名称: {}, 时间: {} ~ {}",
                season.getId(), season.getName(), beginTime, endTime);

        return season.getId();
    }
}

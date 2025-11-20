package com.tianji.learning.handler;

import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;



@Slf4j
@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {

    private final IPointsBoardSeasonService seasonService;

    private final IPointsBoardService pointsBoardService;

    /**
     * 自动创建未来3个月的赛季（每天凌晨1点执行）
     * 确保系统中始终有未来几个月的赛季数据
     */
    @XxlJob("createSeasonJob")
    public void createFutureSeasons() {
        log.info("开始自动创建未来赛季...");

        LocalDateTime now = LocalDateTime.now();

        // 创建当前月及未来3个月的赛季
        for (int i = 0; i < 4; i++) {
            LocalDateTime futureTime = now.plusMonths(i);
            try {
                seasonService.createSeasonByTime(futureTime);
            } catch (Exception e) {
                log.error("创建赛季失败，时间: {}", futureTime, e);
            }
        }

        log.info("自动创建未来赛季完成");
    }

    /**
     * 创建上月赛季的榜单表（每月1号凌晨3点执行）
     * 如果赛季不存在会自动创建
     */
    @XxlJob("createTableJob")
    public void createPointsBoardTableOfLastSeason(){
        log.info("开始创建上月赛季榜单表...");

        // 1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);

        // 2.查询或创建赛季（如果不存在会自动创建）
        Integer season = seasonService.createSeasonByTime(time);
        if (season == null) {
            log.error("创建赛季失败，无法创建榜单表");
            return;
        }

        // 3.创建表
        try {
            pointsBoardService.createPointsBoardTableBySeason(season);
            log.info("创建榜单表成功，赛季ID: {}, 表名: points_board_{}", season, season);
        } catch (Exception e) {
            log.error("创建榜单表失败，赛季ID: {}", season, e);
            return;
        }

        // 4.持久化上月榜单数据到历史表
        try {
            pointsBoardService.persistPointsBoardToHistory(season);
            log.info("持久化榜单数据完成，赛季ID: {}", season);
        } catch (Exception e) {
            log.error("持久化榜单数据失败，赛季ID: {}", season, e);
        }

    }
}
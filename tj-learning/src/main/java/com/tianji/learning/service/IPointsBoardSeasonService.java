package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoardSeason;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author author
 * @since 2025-10-30
 */
public interface IPointsBoardSeasonService extends IService<PointsBoardSeason> {

    List<PointsBoardSeason> queryBoardSeasons();

    Integer querySeasonByTime(LocalDateTime time);

    /**
     * 根据时间创建或获取赛季
     * 如果指定时间的赛季不存在，则自动创建
     * @param time 时间
     * @return 赛季ID
     */
    Integer createSeasonByTime(LocalDateTime time);
}

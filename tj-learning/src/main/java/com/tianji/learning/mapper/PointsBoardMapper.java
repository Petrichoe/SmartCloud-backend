package com.tianji.learning.mapper;

import com.tianji.learning.domain.po.PointsBoard;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 学霸天梯榜 Mapper 接口
 * </p>
 *
 * @author author
 * @since 2025-10-30
 */
public interface PointsBoardMapper extends BaseMapper<PointsBoard> {

    /**
     * 创建榜单表
     * @param tableName 表名
     */
    void createPointsBoardTable(@Param("tableName") String tableName);

    /**
     * 批量插入榜单数据到指定表
     * @param tableName 表名
     * @param list 榜单数据列表
     */
    void batchInsertToTable(@Param("tableName") String tableName, @Param("list") List<PointsBoard> list);

    /**
     * 查询指定用户在历史赛季的榜单记录
     * @param tableName 表名
     * @param userId 用户ID
     * @param season 赛季ID
     * @return 榜单记录
     */
    PointsBoard queryUserHistoryBoard(@Param("tableName") String tableName,
                                      @Param("userId") Long userId,
                                      @Param("season") Integer season);

    /**
     * 分页查询历史赛季榜单列表
     * @param tableName 表名
     * @param season 赛季ID
     * @param offset 偏移量
     * @param pageSize 每页大小
     * @return 榜单列表
     */
    List<PointsBoard> queryHistoryBoardList(@Param("tableName") String tableName,
                                            @Param("season") Integer season,
                                            @Param("offset") Integer offset,
                                            @Param("pageSize") Integer pageSize);

    /**
     * 检查表是否存在
     * @param tableName 表名
     * @return 1-存在，0-不存在
     */
    Integer checkTableExists(@Param("tableName") String tableName);

}

package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constans.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;


import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author author
 * @since 2025-10-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {

    private static final String POINTS_BOARD_TABLE_PREFIX = "points_board_";
    private final StringRedisTemplate redisTemplate;

    private final UserClient userClient;

    @Override
    public PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query) {
        // 1. 判断查询当前赛季还是历史赛季（season为null或0表示当前赛季）
        Long season = query.getSeason();
        boolean isCurrent = season == null || season == 0;

        LocalDateTime now = LocalDateTime.now();
        String key=RedisConstants.POINTS_BOARD_KEY_PREFIX+now.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);

        //2.查询我的积分和排名
        PointsBoard board = isCurrent ?
                queryMyCurrentBoard(key):
                queryMyHistoryBoard(season);

        //3.查询榜单列表
        List<PointsBoard> list= isCurrent ? queryCurrentBoardList(key, query) : queryHistoryBoardList( query);

        //4.封装vo
        PointsBoardVO vo = new PointsBoardVO();
        // 4.1.处理用户不在榜单的情况（board可能为null）
        if (board != null) {
            vo.setRank(board.getRank());
            vo.setPoints(board.getPoints());
        }

        //4.2查询用户名称
        //4.1.1提取所有用户id
        Set<Long> userIds = list.stream().map(PointsBoard::getUserId).collect(Collectors.toSet());
       //4.1.2 批量查询用户信息并转为Map
        Map<Long, UserDTO> userMap=new HashMap<>();
        if (CollUtils.isNotEmpty(userIds)){
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            userMap= users.stream().collect(Collectors.toMap(UserDTO::getId, v -> v));
        }
        Map<Long, UserDTO> finalUserMap = userMap; // 用于lambda中使用

        //4.1.3封装BoardList数据
        vo.setBoardList(list.stream().map(b -> {
            PointsBoardItemVO item = new PointsBoardItemVO();
            item.setPoints(b.getPoints());
            item.setRank(b.getRank());
            UserDTO userDTO = finalUserMap.get(b.getUserId());
            if (userDTO != null) {
                item.setName(userDTO.getName());
            }
            return item;
        }).collect(Collectors.toList()));
        return vo;
    }

    /**
     * 从Redis查询当前赛季当前用户的积分和排名
     * @param key Redis key
     * @return PointsBoard对象，如果用户不在榜单则返回null
     */
    private PointsBoard queryMyCurrentBoard(String key) {
        // 1. 获取当前登录用户ID
        Long userId = UserContext.getUser();

        // 2. 从Redis ZSet查询用户的积分（score）
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());

        // 3. 如果积分为null，说明用户不在榜单中
        if (score == null) {
            return null;
        }

        // 4. 从Redis ZSet查询用户的排名（reverseRank：按score降序排名，从0开始）
        Long rank = redisTemplate.opsForZSet().reverseRank(key, userId.toString());

        // 5. 封装返回结果
        PointsBoard board = new PointsBoard();
        board.setPoints(score.intValue());
        board.setRank(rank == null ? null : rank.intValue() + 1); // 排名+1，从1开始

        return board;
    }

    /**
     * 从数据库查询历史赛季当前用户的积分和排名
     * @param season 赛季ID
     * @return PointsBoard对象，如果用户不在榜单则返回null
     */
    private PointsBoard queryMyHistoryBoard(Long season) {
        // 1. 获取当前登录用户ID
        Long userId = UserContext.getUser();

        // 2. 构建表名并校验安全性
        String tableName = POINTS_BOARD_TABLE_PREFIX + season;
        validateTableName(tableName, season.intValue());

        // 3. 检查表是否存在
        if (!checkTableExists(tableName)) {
            log.warn("历史榜单表不存在，表名: {}，赛季ID: {}。请先执行定时任务创建表并持久化数据。", tableName, season);
            return null;
        }

        // 4. 从数据库查询指定赛季和用户的榜单记录
        return getBaseMapper().queryUserHistoryBoard(tableName, userId, season.intValue());
    }

    /**
     * 从Redis查询当前赛季的榜单列表（分页）
     * @param key Redis key
     * @param query 分页查询条件
     * @return 榜单列表
     */
    private List<PointsBoard> queryCurrentBoardList(String key, PointsBoardQuery query) {
        // 1. 计算分页参数
        int pageNo = query.getPageNo();
        int pageSize = query.getPageSize();

        // 2. 计算Redis ZSet的起始和结束位置
        // Redis的ZREVRANGE命令：start从0开始，end是包含的
        int start = (pageNo - 1) * pageSize;
        int end = start + pageSize - 1;

        // 3. 从Redis ZSet按score降序分页查询（reverseRangeWithScores：降序+score）
        Set<ZSetOperations.TypedTuple<String>> tuples =
            redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);

        // 4. 判空
        if (CollUtils.isEmpty(tuples)) {
            return CollUtils.emptyList();
        }

        // 5. 封装结果
        int rank = start + 1; // 排名从1开始
        List<PointsBoard> list = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String userIdStr = tuple.getValue();
            Double score = tuple.getScore();

            if (userIdStr != null && score != null) {
                PointsBoard board = new PointsBoard();
                board.setUserId(Long.valueOf(userIdStr));
                board.setPoints(score.intValue());
                board.setRank(rank++);
                list.add(board);
            }
        }

        return list;
    }

    /**
     * 从数据库查询历史赛季的榜单列表（分页）
     * @param query 分页查询条件
     * @return 榜单列表
     */
    private List<PointsBoard> queryHistoryBoardList(PointsBoardQuery query) {
        // 1. 获取赛季ID
        Long season = query.getSeason();
        if (season == null || season <= 0) {
            return CollUtils.emptyList();
        }

        // 2. 构建表名并校验安全性
        String tableName = POINTS_BOARD_TABLE_PREFIX + season;
        validateTableName(tableName, season.intValue());

        // 3. 检查表是否存在
        if (!checkTableExists(tableName)) {
            log.warn("历史榜单表不存在，表名: {}，赛季ID: {}。请先执行定时任务创建表并持久化数据。", tableName, season);
            return CollUtils.emptyList();
        }

        // 4. 计算分页参数
        int pageNo = query.getPageNo();
        int pageSize = query.getPageSize();
        int offset = (pageNo - 1) * pageSize;

        // 5. 从数据库分页查询指定赛季的榜单记录，按rank升序排列
        return getBaseMapper().queryHistoryBoardList(tableName, season.intValue(), offset, pageSize);
    }

    /**
     * 创建数据库表根据id
     * @param season
     */
    @Override
    public void createPointsBoardTableBySeason(Integer season) {
        String tableName = POINTS_BOARD_TABLE_PREFIX + season;
        // 校验表名安全性
        validateTableName(tableName, season);
        getBaseMapper().createPointsBoardTable(tableName);
    }

    /**
     * 持久化上月榜单数据到历史表
     * @param season 赛季ID
     */
    @Override
    public void persistPointsBoardToHistory(Integer season) {
        // 1. 参数校验
        if (season == null || season < 1) {
            throw new IllegalArgumentException("赛季ID无效: " + season);
        }

        // 2. 构建表名并校验安全性
        String tableName = POINTS_BOARD_TABLE_PREFIX + season;
        validateTableName(tableName, season);

        // 3. 构建Redis key（上个月的key）
        LocalDateTime lastMonth = LocalDateTime.now().minusMonths(1);
        String redisKey = RedisConstants.POINTS_BOARD_KEY_PREFIX +
                         lastMonth.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);

        // 4. 从Redis读取前100名数据
        Set<ZSetOperations.TypedTuple<String>> top100 =
            redisTemplate.opsForZSet().reverseRangeWithScores(redisKey, 0, 99);

        // 5. 判空：如果没有数据，直接返回
        if (CollUtils.isEmpty(top100)) {
            log.warn("Redis中没有上月榜单数据，key: {}", redisKey);
            return;
        }

        // 6. 转换数据：构建PointsBoard列表（包含rank和season）
        List<PointsBoard> boardList = new ArrayList<>(top100.size());
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : top100) {
            String userIdStr = tuple.getValue();
            Double score = tuple.getScore();

            if (userIdStr != null && score != null) {
                PointsBoard board = new PointsBoard();
                board.setUserId(Long.valueOf(userIdStr));
                board.setPoints(score.intValue());
                board.setRank(rank++);
                board.setSeason(season);
                boardList.add(board);
            }
        }

        // 7. 批量插入到历史表
        if (CollUtils.isNotEmpty(boardList)) {
            getBaseMapper().batchInsertToTable(tableName, boardList);
            log.info("持久化榜单数据成功，赛季: {}, 表名: {}, 数据量: {}",
                    season, tableName, boardList.size());
        }

        // 8. ：清理Redis中的旧数据（节省内存）
         redisTemplate.delete(redisKey);
         log.info("清理Redis旧数据，key: {}", redisKey);
    }

    /**
     * 校验表名是否合法，防止SQL注入
     * @param tableName 表名
     * @param season 赛季ID
     */
    private void validateTableName(String tableName, Integer season) {
        // 1. 表名不能为空
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("表名不能为空");
        }

        // 2. 必须以指定前缀开头
        if (!tableName.startsWith(POINTS_BOARD_TABLE_PREFIX)) {
            throw new IllegalArgumentException("表名必须以 " + POINTS_BOARD_TABLE_PREFIX + " 开头");
        }

        // 3. 长度限制（5-64字符）
        if (tableName.length() < 5 || tableName.length() > 64) {
            throw new IllegalArgumentException("表名长度必须在5-64字符之间");
        }

        // 4. 只允许字母、数字、下划线（防止SQL注入）
        if (!tableName.matches("^[a-zA-Z0-9_]{5,64}$")) {
            throw new IllegalArgumentException("表名只能包含字母、数字、下划线");
        }

        // 5. 验证赛季ID部分是否一致
        String expectedTableName = POINTS_BOARD_TABLE_PREFIX + season;
        if (!tableName.equals(expectedTableName)) {
            throw new IllegalArgumentException("表名与赛季ID不匹配");
        }
    }

    /**
     * 检查表是否存在
     * @param tableName 表名
     * @return true-存在，false-不存在
     */
    private boolean checkTableExists(String tableName) {
        Integer count = getBaseMapper().checkTableExists(tableName);
        return count != null && count > 0;
    }
}

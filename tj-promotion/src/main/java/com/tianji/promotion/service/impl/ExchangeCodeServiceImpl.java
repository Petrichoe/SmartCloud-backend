package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.query.CodeQuery;
import com.tianji.promotion.domain.vo.ExchangeCodePageVO;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.ExchangeCodeMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.tianji.promotion.constants.RedisConstants.*;
import static com.tianji.promotion.enums.ExchangeCodeStatus.*;

/**
 * <p>
 * 兑换码 服务实现类
 * </p>
 *
 * @author kevin
 * @since 2025-11-10
 */
@Service
public class ExchangeCodeServiceImpl extends ServiceImpl<ExchangeCodeMapper, ExchangeCode> implements IExchangeCodeService {
    private final StringRedisTemplate redisTemplate;
    private final BoundValueOperations<String, String> serialOps;

    public ExchangeCodeServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.serialOps = redisTemplate.boundValueOps(COUPON_CODE_SERIAL_KEY);
    }

    @Override
    @Async("generateExchangeCodeExecutor")
    public void asyncGenerateCode(Coupon coupon) {
        // 发放数量
        Integer totalNum = coupon.getTotalNum();
        // 1.获取Redis自增序列号
        Long result = serialOps.increment(totalNum);
        if (result == null) {
            return;
        }
        int maxSerialNum = result.intValue();
        List<ExchangeCode> list = new ArrayList<>(totalNum);
        for (int serialNum = maxSerialNum - totalNum + 1; serialNum <= maxSerialNum; serialNum++) {
            // 2.生成兑换码
            String code = CodeUtil.generateCode(serialNum, coupon.getId());
            ExchangeCode e = new ExchangeCode();
            e.setCode(code);
            e.setId(serialNum);
            e.setExchangeTargetId(coupon.getId());
            e.setExpiredTime(coupon.getIssueEndTime());
            list.add(e);
        }
        // 3.保存数据库
        saveBatch(list);

        // 4.写入Redis缓存，member：couponId，score：兑换码的最大序列号
        redisTemplate.opsForZSet().add(COUPON_RANGE_KEY, coupon.getId().toString(), maxSerialNum);
    }

    @Override
    public PageDTO<ExchangeCodePageVO> queryCodePage(CodeQuery query) {
        // 1.分页查询
        Page<ExchangeCode> page = lambdaQuery()
                .eq(query.getCouponId() != null, ExchangeCode::getExchangeTargetId, query.getCouponId())
                .eq(query.getStatus() != null, ExchangeCode::getStatus, query.getStatus())
                .page(query.toMpPage());
        // 2.转换VO
        List<ExchangeCode> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return PageDTO.empty(page);
        }
        List<ExchangeCodePageVO> list = BeanUtils.copyList(records, ExchangeCodePageVO.class);
        // 3.返回分页结果
        return PageDTO.of(page, list);
    }

    @Override
    public ExchangeCodeStatus checkCodeStatus(String code) {
        // 1.解析兑换码，得到序列号
        long serialNum = CodeUtil.parseCode(code);
        // 2.从BitMap中查询兑换码是否已使用
        Boolean used = redisTemplate.opsForValue().getBit(COUPON_CODE_STATUS_KEY, serialNum);
        // 3.如果已使用，直接返回已使用状态
        if (Boolean.TRUE.equals(used)) {
            return USED;
        }
        // 4.未使用，需要查询数据库判断是否过期
        ExchangeCode exchangeCode = getById(serialNum);
        if (exchangeCode == null) {
            throw new BadRequestException("兑换码不存在");
        }
        // 5.判断是否过期
        LocalDateTime now = LocalDateTime.now();
        if (exchangeCode.getExpiredTime() != null && now.isAfter(exchangeCode.getExpiredTime())) {
            return EXPIRED;
        }
        // 6.未使用且未过期
        return UNUSED;
    }

    @Override
    public boolean updateCodeStatus(long serialNum, boolean mark) {
        // 更新BitMap中的兑换码状态
        Boolean result = redisTemplate.opsForValue().setBit(COUPON_CODE_STATUS_KEY, serialNum, mark);
        // setBit返回的是修改前的值，如果修改前的值与要设置的值相同，说明没有实际更新
        return !Boolean.valueOf(mark).equals(result);
    }

    @Override
    public void updateCodeExpiredTime(Long couponId, LocalDateTime expiredTime) {
        // 更新指定优惠券的所有兑换码过期时间
        lambdaUpdate()
                .set(ExchangeCode::getExpiredTime, expiredTime)
                .eq(ExchangeCode::getExchangeTargetId, couponId)
                .update();
    }

}

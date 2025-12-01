package com.tianji.promotion.service.impl;

import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.RedisConstants;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author kevin
 * @since 2025-11-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;

    private final IExchangeCodeService exchangeCodeService;

    private final RabbitTemplate rabbitTemplate;

    private final StringRedisTemplate redisTemplate;

    private static final RedisScript<Long> RECEIVE_COUPON_SCRIPT;

    static {
        RECEIVE_COUPON_SCRIPT = RedisScript.of(new ClassPathResource("lua/receive_coupon.lua"), Long.class);
    }


    @Override
    public void getUserCoupon(Long couponId) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("用户未登录");
        }

        String cacheKey = RedisConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        String userCouponKey = RedisConstants.COUPON_CACHE_KEY_PREFIX + couponId
                + RedisConstants.USER_COUPON_CACHE_KEY_SUFFIX;

        Long result = redisTemplate.execute(
                RECEIVE_COUPON_SCRIPT,
                java.util.List
                        .of(cacheKey, userCouponKey),
                userId.toString()
        );

        if (result == null || result != 0) {
            String errorMsg;
            switch (result == null ? -1 : result.intValue()) {
                case 1:
                    errorMsg = "优惠券未开始发放或已结束";
                    break;
                case 2:
                    errorMsg = "优惠券库存不足";
                    break;
                case 3:
                    errorMsg = "优惠券发放已经结束";
                    break;
                case 4:
                    errorMsg = "超出领取数量";
                    break;
                default:
                    errorMsg = "领取失败";
                    break;
            }
            throw new BadRequestException(errorMsg);
        }

        UserCouponDTO dto = new UserCouponDTO();
        dto.setUserId(userId);
        dto.setCouponId(couponId);
        try {
            rabbitTemplate.convertAndSend(
                    MqConstants.Exchange.Promotion_EXCHANGE,
                    MqConstants.Key.COUPON_RECEIVE,
                    dto
            );
            log.info("优惠券领取请求已发送到MQ: userId={}, couponId={}", userId, couponId);
        } catch (Exception e) {
            rollbackRedisStock(couponId);
            rollbackUserCouponCount(couponId, userId);
            log.error("发送优惠券领取消息失败: userId={}, couponId={}", userId, couponId, e);
            throw new BizIllegalException("系统繁忙，请稍后再试");
        }
    }

    @Override
    public void asyncReceiveCoupon(UserCouponDTO dto) {
        Coupon coupon = couponMapper.selectById(dto.getCouponId());
        if (coupon == null) {
            log.error("优惠券不存在: couponId={}", dto.getCouponId());
            rollbackRedisStock(dto.getCouponId());
            rollbackUserCouponCount(dto.getCouponId(), dto.getUserId());
            return;
        }

        try {
            IUserCouponService proxy = (IUserCouponService) AopContext.currentProxy();
            proxy.checkAndCreateUserCoupon(coupon, dto.getUserId(), null);
            log.info("优惠券领取成功: userId={}, couponId={}", dto.getUserId(), dto.getCouponId());
        } catch (Exception e) {
            rollbackRedisStock(dto.getCouponId());
            rollbackUserCouponCount(dto.getCouponId(), dto.getUserId());
            log.error("优惠券领取失败: userId={}, couponId={}, error={}",
                    dto.getUserId(), dto.getCouponId(), e.getMessage());
        }
    }

    private void rollbackRedisStock(Long couponId) {
        String cacheKey = RedisConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        redisTemplate.opsForHash().increment(cacheKey, "totalNum", 1);
        log.info("回滚Redis库存: couponId={}", couponId);
    }

    private void rollbackUserCouponCount(Long couponId, Long userId) {
        String userCouponKey = RedisConstants.COUPON_CACHE_KEY_PREFIX + couponId
                + RedisConstants.USER_COUPON_CACHE_KEY_SUFFIX;
        redisTemplate.opsForHash().increment(userCouponKey, userId.toString(), -1);
        log.info("回滚用户领取数量: couponId={}, userId={}", couponId, userId);
    }

    @Transactional // 这里进事务，同时，事务方法一定要public修饰
    @Override
    public void checkAndCreateUserCoupon(Coupon coupon, Long userId, Integer serialNum){
        // 1.校验每人限领数量 - 已在Lua脚本中完成，此处无需再校验

        // 2.更新优惠券的已经发放的数量 + 1
        int r = couponMapper.incrIssueNum(coupon.getId());
        if (r == 0) {
            throw new BizIllegalException("优惠券库存不足");
        }
        // 3.新增一个用户券
        saveUserCoupon(coupon, userId);
        // 4.更新兑换码状态
        if (serialNum != null) {
            exchangeCodeService.lambdaUpdate()
                    .set(ExchangeCode::getUserId, userId)
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .eq(ExchangeCode::getId, serialNum)
                    .update();
        }
    }

    /**
     * 兑换码兑换优惠券
     * @param code
     */
    @Override
    @Transactional
    public void exchangeCoupon(String code) {
        // 1.校验兑换码状态
        ExchangeCodeStatus exchangeCodeStatus = exchangeCodeService.checkCodeStatus(code);
        if (exchangeCodeStatus == ExchangeCodeStatus.USED){
            throw new BadRequestException("兑换码已使用");
        }
        if (exchangeCodeStatus == ExchangeCodeStatus.EXPIRED){
            throw new BadRequestException("兑换码已过期");
        }

        // 2.根据兑换码查询兑换码信息（获取优惠券ID）
        ExchangeCode exchangeCode = exchangeCodeService.lambdaQuery()
                .eq(ExchangeCode::getCode, code)
                .one();
        if (exchangeCode == null) {
            throw new BadRequestException("兑换码不存在");
        }

        // 3.调用领取优惠券方法（复用逻辑）
        Long couponId = exchangeCode.getExchangeTargetId();
        getUserCoupon(couponId);

        // 4.标记兑换码已使用
        exchangeCodeService.updateCodeStatus(exchangeCode.getId(), true);
    }

    private void saveUserCoupon(Coupon coupon, Long userId) {
        // 1.基本信息
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(coupon.getId());
        // 2.有效期信息
        LocalDateTime termBeginTime = coupon.getTermBeginTime();
        LocalDateTime termEndTime = coupon.getTermEndTime();
        if (termBeginTime == null) {
            termBeginTime = LocalDateTime.now();
            termEndTime = termBeginTime.plusDays(coupon.getTermDays());
        }
        uc.setTermBeginTime(termBeginTime);
        uc.setTermEndTime(termEndTime);
        // 3.保存
        save(uc);
    }
}

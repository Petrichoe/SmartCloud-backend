package com.tianji.promotion.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.query.CodeQuery;
import com.tianji.promotion.domain.vo.ExchangeCodePageVO;
import com.tianji.promotion.enums.ExchangeCodeStatus;

import java.time.LocalDateTime;

/**
 * <p>
 * 兑换码 服务类
 * </p>
 *
 * @author kevin
 * @since 2025-11-10
 */
public interface IExchangeCodeService extends IService<ExchangeCode> {

    void asyncGenerateCode(Coupon coupon);

    PageDTO<ExchangeCodePageVO> queryCodePage(CodeQuery query);

    /**
     * 校验兑换码状态
     * @param code 兑换码
     * @return 兑换码状态
     */
    ExchangeCodeStatus checkCodeStatus(String code);

    /**
     * 标记兑换码已使用（更新BitMap）
     * @param serialNum 兑换码序列号
     * @param mark true-标记已使用，false-标记未使用
     * @return 是否更新成功
     */
    boolean updateCodeStatus(long serialNum, boolean mark);

    /**
     * 更新指定优惠券的所有兑换码过期时间
     * @param couponId 优惠券id
     * @param expiredTime 新的过期时间
     */
    void updateCodeExpiredTime(Long couponId, LocalDateTime expiredTime);
}

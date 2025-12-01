package com.tianji.promotion.utils;

import com.tianji.common.constants.RegexConstants;
import com.tianji.common.exceptions.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * <h1 style='font-weight:500'>1.兑换码算法说明：</h1>
 * <p>兑换码分为明文和密文，明文是50位二进制数，密文是长度为10的Base32编码的字符串 </p>
 * <h1 style='font-weight:500'>2.兑换码的明文结构：</h1>
 * <p style='padding: 0 15px'>14(校验码) + 4 (新鲜值) + 32(序列号) </p>
 *   <ul style='padding: 0 15px'>
 *       <li>序列号：一个单调递增的数字，可以通过Redis来生成</li>
 *       <li>新鲜值：可以是优惠券id的最后4位，同一张优惠券的兑换码就会有一个相同标记</li>
 *       <li>载荷：将新鲜值（4位）拼接序列号（32位）得到载荷</li>
 *       <li>校验码：将载荷4位一组，每组乘以加权数，最后累加求和，然后对2^14求余得到</li>
 *   </ul>
 *  <h1 style='font-weight:500'>3.兑换码的加密过程：</h1>
 *     <ol type='a' style='padding: 0 15px'>
 *         <li>首先利用优惠券id计算新鲜值 f</li>
 *         <li>将f和序列号s拼接，得到载荷payload</li>
 *         <li>然后以f为角标，从动态生成的16组加权码表中选一组</li>
 *         <li>对payload做加权计算，得到校验码 c  </li>
 *         <li>利用c的后5位做角标，从动态生成的异或密钥表中选择一个密钥：key</li>
 *         <li>将payload与key做异或，作为新payload2</li>
 *         <li>然后拼接兑换码明文：c (14位) + payload2（36位）</li>
 *         <li>利用Base32对密文转码，生成兑换码</li>
 *     </ol>
 * <h1 style='font-weight:500'>4.兑换码的解密过程：</h1>
 * <ol type='a' style='padding: 0 15px'>
 *      <li>首先利用Base32解码兑换码，得到明文数值num</li>
 *      <li>取num的高14位得到c1，取num低36位得payload </li>
 *      <li>利用c1的后5位做角标，从动态生成的异或密钥表中选择一个密钥：key</li>
 *      <li>将payload与key做异或，作为新payload2</li>
 *      <li>利用加密时的算法，用payload2和f计算出新校验码c2，把c1和c2比较，一致则通过 </li>
 * </ol>
 *
 * <h1 style='font-weight:500'>5.安全加固（2025-11-30）：</h1>
 * <ul style='padding: 0 15px'>
 *     <li>密钥表不再硬编码，改为通过CodeSecurityProvider动态生成</li>
 *     <li>密钥种子从配置文件读取，支持环境变量和配置中心</li>
 *     <li>可定期轮换密钥，提升安全性</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CodeUtil {

    /**
     * 安全密钥提供者，负责动态生成密钥表
     */
    private final CodeSecurityProvider securityProvider;
    /**
     * fresh值的偏移位数
     */
    private final static int FRESH_BIT_OFFSET = 32;
    /**
     * 校验码的偏移位数
     */
    private final static int CHECK_CODE_BIT_OFFSET = 36;
    /**
     * fresh值的掩码，4位
     */
    private final static int FRESH_MASK = 0xF;
    /**
     * 验证码的掩码，14位
     */
    private final static int CHECK_CODE_MASK = 0b11111111111111;
    /**
     * 载荷的掩码，36位
     */
    private final static long PAYLOAD_MASK = 0xFFFFFFFFFL;
    /**
     * 序列号掩码，32位
     */
    private final static long SERIAL_NUM_MASK = 0xFFFFFFFFL;

    /**
     * 生成兑换码
     *
     * @param serialNum 递增序列号
     * @param fresh 新鲜值（通常为优惠券ID）
     * @return 兑换码
     */
    public String generateCode(long serialNum, long fresh) {
        // 1.计算新鲜值
        fresh = fresh & FRESH_MASK;
        // 2.拼接payload，fresh（4位） + serialNum（32位）
        long payload = fresh << FRESH_BIT_OFFSET | serialNum;
        // 3.计算验证码
        long checkCode = calcCheckCode(payload, (int) fresh);
        // 4.payload做大质数异或运算，混淆数据（使用动态生成的密钥表）
        payload ^= securityProvider.getXorTable()[(int) (checkCode & 0b11111)];
        // 5.拼接兑换码明文: 校验码（14位） + payload（36位）
        long code = checkCode << CHECK_CODE_BIT_OFFSET | payload;
        // 6.转码
        return Base32.encode(code);
    }

    private long calcCheckCode(long payload, int fresh) {
        // 1.获取码表（从动态生成的密钥表）
        int[] table = securityProvider.getPrimeTable()[fresh];
        // 2.生成校验码，payload每4位乘加权数，求和，取最后14位结果
        long sum = 0;
        int index = 0;
        while (payload > 0) {
            sum += (payload & 0xf) * table[index++];
            payload >>>= 4;
        }
        return sum & CHECK_CODE_MASK;
    }

    public long parseCode(String code) {
        if (code == null || !code.matches(RegexConstants.COUPON_CODE_PATTERN)) {
            // 兑换码格式错误
            throw new BadRequestException("无效兑换码");
        }
        // 1.Base32解码
        long num = Base32.decode(code);
        // 2.获取低36位，payload
        long payload = num & PAYLOAD_MASK;
        // 3.获取高14位，校验码
        int checkCode = (int) (num >>> CHECK_CODE_BIT_OFFSET);
        // 4.载荷异或大质数，解析出原来的payload（使用动态生成的密钥表）
        payload ^= securityProvider.getXorTable()[(checkCode & 0b11111)];
        // 5.获取高4位，fresh
        int fresh = (int) (payload >>> FRESH_BIT_OFFSET & FRESH_MASK);
        // 6.验证格式：
        if (calcCheckCode(payload, fresh) != checkCode) {
            throw new BadRequestException("无效兑换码");
        }
        return payload & SERIAL_NUM_MASK;
    }
}
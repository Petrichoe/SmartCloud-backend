package com.tianji.promotion.utils;

import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * 兑换码安全密钥提供者
 * 负责基于外部密钥动态生成XOR表和质数表，避免硬编码泄露风险
 *
 * @author kevin
 * @since 2025-11-30
 */
@Getter
public class CodeSecurityProvider {

    /**
     * 异或密钥表，用于最后的数据混淆
     */
    private final long[] xorTable;

    /**
     * 序列号加权运算的质数表
     */
    private final int[][] primeTable;

    /**
     * 大质数表，用于生成XOR密钥
     */
    private static final long[] LARGE_PRIMES = {
            45139281907L, 61261925523L, 58169127203L, 27031786219L,
            64169927199L, 46169126943L, 32731286209L, 52082227349L,
            59169127063L, 36169126987L, 52082200939L, 61261925739L,
            32731286563L, 27031786427L, 56169127077L, 34111865001L,
            52082216763L, 61261925663L, 56169127113L, 45139282119L,
            32731286479L, 64169927233L, 41390251661L, 59169127121L,
            64169927321L, 55139282179L, 34111864881L, 46169127031L,
            58169127221L, 61261925523L, 36169126943L, 64169927363L,
            72169927483L, 48139282211L, 39111865099L, 51169127177L,
            67169927511L, 44139282307L, 29731286677L, 56082227429L
    };

    /**
     * 小质数表，用于生成加权质数
     */
    private static final int[] SMALL_PRIMES = {
            19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97,
            101, 103, 107, 109, 113, 127, 131, 137, 139, 149, 151, 157, 163, 167, 173, 179, 181, 191, 193, 197, 199,
            211, 223, 227, 229, 233, 239, 241, 251, 257, 263, 269, 271, 277, 281, 283, 293,
            307, 311, 313, 317, 331, 337, 347, 349, 353, 359, 367, 373, 379, 383, 389, 397,
            401, 409, 419, 421, 431, 433, 439, 443, 449, 457, 461, 463, 467, 479, 487, 491, 499,
            503, 509, 521, 523, 541, 547, 557, 563, 569, 571, 577, 587, 593, 599,
            601, 607, 613, 617, 619, 631, 641, 643, 647, 653, 659, 661, 673, 677, 683, 691,
            701, 709, 719, 727, 733, 739, 743, 751, 757, 761, 769, 773, 787, 797,
            809, 811, 821, 823, 827, 829, 839, 853, 857, 859, 863, 877, 881, 883, 887,
            907, 911, 919, 929, 937, 941, 947, 953, 967, 971, 977, 983, 991, 997,
            1009, 1013, 1019, 1021, 1031, 1033, 1039, 1049, 1051, 1061, 1063, 1069, 1087, 1091, 1093, 1097,
            1103, 1109, 1117, 1123, 1129, 1151, 1153, 1163, 1171, 1181, 1187, 1193,
            1201, 1213, 1217, 1223, 1229, 1231, 1237, 1249, 1259, 1277, 1279, 1283, 1289, 1291, 1297,
            1301, 1303, 1307, 1319, 1321, 1327, 1361, 1367, 1373, 1381, 1399,
            1409, 1423, 1427, 1429, 1433, 1439, 1447, 1451, 1453, 1459, 1471, 1481, 1483, 1487, 1489, 1493, 1499,
            1511, 1523, 1531, 1543, 1549, 1553, 1559, 1567, 1571, 1579, 1583, 1597,
            1601, 1607, 1609, 1613, 1619, 1621, 1627, 1637, 1657, 1663, 1667, 1669, 1693, 1697, 1699
    };

    /**
     * 构造函数
     * @param xorSecret XOR密钥种子
     * @param primeSecret 质数表密钥种子
     */
    public CodeSecurityProvider(String xorSecret, String primeSecret) {
        this.xorTable = generateXorTable(xorSecret);
        this.primeTable = generatePrimeTable(primeSecret);
    }

    /**
     * 基于密钥动态生成XOR表
     * @param secret 密钥种子
     * @return 32个大质数组成的XOR表
     */
    private long[] generateXorTable(String secret) {
        long[] table = new long[32];
        for (int i = 0; i < 32; i++) {
            // 使用SHA256(secret + index)生成哈希值
            byte[] hash = sha256(secret + ":" + i);
            // 将哈希值转换为索引，选择大质数
            int primeIndex = Math.abs(bytesToInt(hash)) % LARGE_PRIMES.length;
            table[i] = LARGE_PRIMES[primeIndex];
        }
        return table;
    }

    /**
     * 基于密钥动态生成质数表
     * @param secret 密钥种子
     * @return 16组质数表，每组10个质数
     */
    private int[][] generatePrimeTable(String secret) {
        int[][] table = new int[16][];
        for (int i = 0; i < 16; i++) {
            table[i] = generatePrimeRow(secret, i);
        }
        return table;
    }

    /**
     * 生成单行质数表
     * @param secret 密钥种子
     * @param rowIndex 行索引
     * @return 10个质数
     */
    private int[] generatePrimeRow(String secret, int rowIndex) {
        int[] row = new int[10];
        byte[] hash = sha256(secret + ":row:" + rowIndex);

        // 基于哈希值选择不同的质数组合
        for (int i = 0; i < 10; i++) {
            int hashIndex = (rowIndex * 10 + i) % hash.length;
            int primeIndex = Math.abs(hash[hashIndex]) % SMALL_PRIMES.length;
            row[i] = SMALL_PRIMES[primeIndex];
        }
        return row;
    }

    /**
     * SHA256哈希
     * @param input 输入字符串
     * @return 哈希字节数组
     */
    private byte[] sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    /**
     * 字节数组转整数
     * @param bytes 字节数组
     * @return 整数
     */
    private int bytesToInt(byte[] bytes) {
        if (bytes.length < 4) {
            bytes = Arrays.copyOf(bytes, 4);
        }
        return ((bytes[0] & 0xFF) << 24) |
               ((bytes[1] & 0xFF) << 16) |
               ((bytes[2] & 0xFF) << 8) |
               (bytes[3] & 0xFF);
    }
}

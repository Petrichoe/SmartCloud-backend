package com.tianji.promotion.config;

import com.tianji.common.constants.MqConstants;
import com.tianji.promotion.utils.CodeSecurityProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
public class PromotionConfig {

    @Value("${tj.promotion.code.xor-secret:tianji-xor-default-key-2025}")
    private String xorSecret;

    @Value("${tj.promotion.code.prime-secret:tianji-prime-default-key-2025}")
    private String primeSecret;

    /**
     * 兑换码安全密钥提供者
     * 从配置文件读取密钥，动态生成密钥表，避免硬编码泄露
     */
    @Bean
    public CodeSecurityProvider codeSecurityProvider() {
        log.info("初始化兑换码安全密钥提供者，密钥已从配置加载");
        return new CodeSecurityProvider(xorSecret, primeSecret);
    }

    @Bean
    public Executor discountSolutionExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 1.核心线程池大小
        executor.setCorePoolSize(12);
        // 2.最大线程池大小
        executor.setMaxPoolSize(12);
        // 3.队列大小
        executor.setQueueCapacity(99999);
        // 4.线程名称
        executor.setThreadNamePrefix("discount-solution-calculator-");
        // 5.拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    @Bean
    public Executor generateExchangeCodeExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 1.核心线程池大小
        executor.setCorePoolSize(2);
        // 2.最大线程池大小
        executor.setMaxPoolSize(5);
        // 3.队列大小
        executor.setQueueCapacity(200);
        // 4.线程名称
        executor.setThreadNamePrefix("exchange-code-handler-");
        // 5.拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 定义优惠券促销交换机
     */
    @Bean
    public TopicExchange promotionExchange() {
        return ExchangeBuilder
                .topicExchange(MqConstants.Exchange.Promotion_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 定义优惠券领取队列
     */
    @Bean
    public Queue couponReceiveQueue() {
        return QueueBuilder
                .durable("promotion.coupon.receive.queue")
                .build();
    }

    /**
     * 绑定优惠券领取队列到交换机
     */
    @Bean
    public Binding couponReceiveBinding() {
        return BindingBuilder
                .bind(couponReceiveQueue())
                .to(promotionExchange())
                .with(MqConstants.Key.COUPON_RECEIVE);
    }
}
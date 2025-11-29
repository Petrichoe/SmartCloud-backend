package com.tianji.promotion.config;

import com.tianji.common.constants.MqConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
public class PromotionConfig {

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
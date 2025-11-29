package com.tianji.promotion.utils;

import com.tianji.common.autoconfigure.redisson.annotations.Lock;
import com.tianji.common.exceptions.BizIllegalException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.Ordered;
@Aspect
@RequiredArgsConstructor
public class MyLockAspect implements Ordered {

    private final RedissonClient redissonClient;

    private final MyLockFactory myLockFactory;

    @Around("@annotation(myLock)")
    public Object tryLock(ProceedingJoinPoint pjp, MyLock myLock) throws Throwable {
        // 1.创建锁对象
        RLock lock = myLockFactory.getLock(myLock.lockType(), myLock.name());
        // 2.尝试获取锁
        boolean isLock = lock.tryLock(myLock.waitTime(), myLock.leaseTime(), myLock.unit());
        // 3.判断是否成功
        if(!isLock) {
            // 3.1.失败，快速结束
            throw new BizIllegalException("请求太频繁");
        }
        try {
            // 3.2.成功，执行业务
            return pjp.proceed();
        } finally {
            // 4.释放锁
            lock.unlock();
        }
    }
    
    @Override
    public int getOrder() {
        return 0;
    }
}
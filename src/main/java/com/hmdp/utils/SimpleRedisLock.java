package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @小羊肖恩
 * @2022/11/21
 * @10:38
 * @Describe：
 */

public class SimpleRedisLock implements ILock {

    
    private String lockName;
    
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String lockName, StringRedisTemplate stringRedisTemplate) {
        this.lockName = lockName;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public static final String KEY_PREFIX = "lock:";
    public static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    public final String key = KEY_PREFIX + lockName;
    
    @Override
    public boolean tryLock(long timeoutSec) {
        
        String key = KEY_PREFIX + lockName;
        
        //获取当前线程的唯一标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        //获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, threadId, timeoutSec, TimeUnit.MINUTES);

        return Boolean.TRUE.equals(success);
        //return success;   若直接返回success，当success为null时会有空指针异常，而使用上述方法，为null时也返回false
        // Boolean --> boolean 自动拆箱时会有空指针风险
    }

    @Override
    public void unlock() {

        //获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取锁中的标识
        String id = stringRedisTemplate.opsForValue().get(key);

        if (threadId.equals(id)) {
            //释放锁
            stringRedisTemplate.delete(key);

        }
    }
}

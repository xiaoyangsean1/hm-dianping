package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @小羊肖恩
 * @2022/11/19
 * @18:48
 * @Describe：
 */

@Slf4j
@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        redisData.setData(value);

        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(       //ID是参数，R是返回值
            String prefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
        //1.从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(prefix + id);

        //2.判断是否命中
        if (StrUtil.isNotBlank(json)) {
            //3.命中，直接返回
            R result = JSONUtil.toBean(json, type);
            return result;
        }

        //判断命中的是否是空值
        if (json != null) {
            return null;
        }

        //4.未命中，根据id查询数据库
        R r = dbFallBack.apply(id);

        //5.判断是否存在
        if (r == null) {
            //将空值写入redis
            this.set(prefix + id, "", time + RandomUtil.randomLong(1, 5), unit);

            //6.不存在，返回错误信息
            return null;
        }

        //7.存在，将商铺数据写入redis中
        this.set(prefix + id, r, time + RandomUtil.randomLong(1, 5), unit);
        //8.返回
        return r;
    }

    //创建一个线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 使用逻辑过期方法解决缓存击穿
     * @param id
     * @return
     */
    public <R, ID> R queryWithLogicalExpire(
            String prefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
        //1.从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(prefix + id);

        //2.判断是否命中
        if (StrUtil.isBlank(json)) {
            //3.未命中，直接返回空值
            return null;
        }

        //4.命中，需要先把json反序列化为对象（用来获取RedisData中的数据）
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        //5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.1.未过期，直接返回商铺信息
            return r;
        }

        //5.2.过期，需要进行缓存重建

        //6.缓存重建
        //6.1.获取互斥锁
        boolean flag = tryLock(LOCK_SHOP_KEY + id);

        //6.2.判断是否获取成功
        if (flag) {
            //6.3.获取成功，开启独立线程进行缓存重建
            //获取成功后应该再次检测redis缓存是否过期，做doublecheck如果存在则无需重建缓存
            json = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            redisData = JSONUtil.toBean(json, RedisData.class);
            r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
            expireTime = redisData.getExpireTime();
            if(expireTime.isAfter(LocalDateTime.now())){
                return r;
            }

            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //查询数据库
                    R result = dbFallBack.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(prefix + id, result, time, unit);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(LOCK_SHOP_KEY + id);
                }
            });


        }

        //6.4.返回过期的商铺信息

        return r;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

}

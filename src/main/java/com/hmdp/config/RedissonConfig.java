package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @小羊肖恩
 * @2022/11/21
 * @12:29
 * @Describe：
 */

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        //配置类
        Config config = new Config();

        //添加redis地址，这里添加单点的地址，也可以使用config.useClusterServers()来添加集群地址
        config.useSingleServer().setAddress("redis://192.168.159.129:6379").setPassword("123123");

        //创建客户端
        return Redisson.create(config);
    }
}

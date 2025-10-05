package com.hmdp.config;

import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.Redisson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host}")
    private String redisHost;

    @Value("${spring.redis.port}")
    private int redisPort;

    @Bean(destroyMethod = "shutdown") // 退出时自动关闭
    public RedissonClient redissonClient() {
        Config config = new Config();
        // 单节点模式
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort);
        return Redisson.create(config);
    }
}


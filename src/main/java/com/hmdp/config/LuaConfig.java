package com.hmdp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

@Configuration
public class LuaConfig {

    @Bean
    public DefaultRedisScript<List> seckillScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        // lua 文件放在 resources/lua/seckill.lua
        script.setLocation(new ClassPathResource("lua/seckill.lua"));
        script.setResultType(List.class); // Lua 返回 {1, orderId} 形式，对应 Java List
        return script;
    }
}

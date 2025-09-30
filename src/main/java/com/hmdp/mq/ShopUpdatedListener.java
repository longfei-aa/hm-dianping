package com.hmdp.mq;

import com.hmdp.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShopUpdatedListener {

    private final StringRedisTemplate stringRedisTemplate;

    public static final String CACHE_SHOP_KEY = "cache:shop:"; // 你项目里已有同名常量就用原来的

    @RabbitListener(queues = RabbitConfig.SHOP_UPDATED_QUEUE)
    public void onShopUpdated(String payload) {
        // payload: {"id": 123}
        Long id = cn.hutool.json.JSONUtil.parseObj(payload).getLong("id");
        if (id == null) return;
        String key = CACHE_SHOP_KEY + id;
        stringRedisTemplate.delete(key); // 幂等
    }
}

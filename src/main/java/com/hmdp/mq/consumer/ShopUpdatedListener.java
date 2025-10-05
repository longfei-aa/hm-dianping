package com.hmdp.mq.consumer;

import com.hmdp.mq.config.ShopMQ;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@Component
public class ShopUpdatedListener {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = ShopMQ.SHOP_UPDATED_QUEUE)
    public void onShopUpdated(String payload) {
        // payload: {"id": 123}
        Long id = cn.hutool.json.JSONUtil.parseObj(payload).getLong("id");
        if (id == null) return;
        String key = CACHE_SHOP_KEY + id;
        stringRedisTemplate.delete(key); // 幂等
    }
}

package com.hmdp.mq.producer;

import com.hmdp.mq.config.SeckillMQ;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SeckillRelay implements InitializingBean {

    private final StringRedisTemplate redis;
    private final RabbitTemplate rabbitTemplate;

    private static final String STREAM_KEY = "seckill:queue:global";
    private static final String GROUP = "g1";
    private final String consumerName = "c-" + UUID.randomUUID();

    @Override
    public void afterPropertiesSet() {
        try {
            // 不存在则创建消费组（MKSTREAM）
            redis.opsForStream().createGroup(STREAM_KEY, ReadOffset.latest(), GROUP);
        } catch (Exception ignore) { /* 组已存在会报错，忽略即可 */ }
    }

    @Scheduled(fixedDelay = 200)
    public void pump() {
        // 先处理 pending（历史未确认）——防止永远卡在 PEL
        readAndHandle(ReadOffset.from("0-0"));
        // 再处理新消息
        readAndHandle(ReadOffset.lastConsumed());
    }

    private void readAndHandle(ReadOffset offset) {
        List<MapRecord<String, Object, Object>> msgs = redis.opsForStream().read(
                Consumer.from(GROUP, consumerName),
                StreamReadOptions.empty().count(200).block(Duration.ofMillis(200)),
                StreamOffset.create(STREAM_KEY, offset)
        );
        if (msgs == null || msgs.isEmpty()) return;

        for (MapRecord<String, Object, Object> m : msgs) {
            Map<Object, Object> v = m.getValue();
            String payload = cn.hutool.json.JSONUtil.createObj()
                    .set("orderId",   v.get("orderId"))
                    .set("userId",    v.get("userId"))
                    .set("voucherId", v.get("voucherId"))
                    .toString();
            try {
                // 发布到 RabbitMQ（确保开启 publisher confirm & 持久化）
                rabbitTemplate.convertAndSend(SeckillMQ.EXCHANGE, SeckillMQ.ROUTING_CREATE, payload);
                // 成功再 ACK
                redis.opsForStream().acknowledge(STREAM_KEY, GROUP, m.getId());
            } catch (Exception e) {
                // 不 ack，留在 pending，后续会走上面的 pending 流程重试
            }
        }
    }
}

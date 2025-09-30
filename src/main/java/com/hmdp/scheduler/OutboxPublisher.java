package com.hmdp.scheduler;

import com.hmdp.config.RabbitConfig;
import com.hmdp.entity.Outbox;
import com.hmdp.mapper.OutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private OutboxMapper outboxMapper;
    private RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 1000)
    public void publishOutbox() {
        List<Outbox> list = outboxMapper.fetchUnsent(100);
        for (Outbox o : list) {
            try {
                rabbitTemplate.convertAndSend(
                        RabbitConfig.SHOP_EXCHANGE,
                        RabbitConfig.SHOP_UPDATED_ROUTING,
                        o.getPayload()
                );
                outboxMapper.markSent(o.getId());
            } catch (Exception ex) {
                // 指数退避：1,2,4,8,16 秒…
                int delay = (int) Math.min(60, Math.pow(2, Math.max(0, o.getRetryCount())));
                outboxMapper.markRetryLater(o.getId(), delay);
                // log.warn("publish outbox fail id={}, delay={}s", o.getId(), delay, ex);
            }
        }
    }
}

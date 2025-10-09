package com.hmdp.mq.producer;

import com.hmdp.mq.config.BlogMQ;
import com.hmdp.mq.config.ShopMQ;
import com.hmdp.entity.Outbox;
import com.hmdp.mapper.OutboxMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Component
public class OutboxPublisher {

    @Resource
    private OutboxMapper outboxMapper;
    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 每 1 秒扫描一次未发送的 Outbox 事件
     */
    @Scheduled(fixedDelay = 1000)
    public void publishOutbox() {
        // 查询未发送的记录（sent=0, next_retry_at<=now），最多取 100 条
        List<Outbox> list = outboxMapper.fetchUnsent(100);
        if (list == null || list.isEmpty()) {
            return;
        }

        for (Outbox o : list) {
            try {
                // 根据事件类型路由到不同的 MQ 交换机
                String eventType = o.getEventType();
                if ("SHOP_UPDATED".equalsIgnoreCase(eventType)) {
                    rabbitTemplate.convertAndSend(
                            ShopMQ.SHOP_EXCHANGE,
                            ShopMQ.SHOP_UPDATED_ROUTING,
                            o.getPayload()
                    );
                } else if ("BLOG_CHANGED".equalsIgnoreCase(eventType)) {
                    rabbitTemplate.convertAndSend(
                            BlogMQ.BLOG_EXCHANGE,
                            BlogMQ.BLOG_CHANGED_ROUTING,
                            o.getPayload()
                    );
                } else {
                    // 未识别的事件类型，直接标记已发送避免死循环
                    outboxMapper.markSent(o.getId());
                    continue;
                }

                // 发送成功 -> 更新状态为 sent=1
                outboxMapper.markSent(o.getId());
            } catch (Exception ex) {
                // 指数退避：1,2,4,8,16 秒…
                int delay = (int) Math.min(60, Math.pow(2, Math.max(0, o.getRetryCount())));
                outboxMapper.markRetryLater(o.getId(), delay);
            }
        }
    }
}

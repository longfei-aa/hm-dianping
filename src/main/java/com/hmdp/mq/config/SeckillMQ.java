package com.hmdp.mq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeckillMQ {
    public static final String EXCHANGE = "seckill.topic";
    public static final String ROUTING_CREATE = "order.create";
    public static final String QUEUE_CREATE = "seckill.order.create.q";

    @Bean TopicExchange seckillExchange() { return new TopicExchange(EXCHANGE, true, false); }
    @Bean Queue createQueue() { return new Queue(QUEUE_CREATE, true); }
    @Bean Binding createBinding() {
        return BindingBuilder.bind(createQueue()).to(seckillExchange()).with(ROUTING_CREATE);
    }
}

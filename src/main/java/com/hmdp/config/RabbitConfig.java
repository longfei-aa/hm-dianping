package com.hmdp.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitConfig {
    public static final String SHOP_EXCHANGE = "shop.topic";
    public static final String SHOP_UPDATED_ROUTING = "SHOP_UPDATED";
    public static final String SHOP_UPDATED_QUEUE = "shop.updated.q";

    @Bean
    public TopicExchange shopExchange() {
        return new TopicExchange(SHOP_EXCHANGE, true, false);
    }

    @Bean
    public Queue shopUpdatedQueue() {
        return new Queue(SHOP_UPDATED_QUEUE, true);
    }

    @Bean
    public Binding shopUpdatedBinding() {
        return BindingBuilder.bind(shopUpdatedQueue())
                .to(shopExchange())
                .with(SHOP_UPDATED_ROUTING);
    }
}

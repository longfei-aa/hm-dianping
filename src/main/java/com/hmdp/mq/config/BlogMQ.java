package com.hmdp.mq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BlogMQ {
    public static final String BLOG_EXCHANGE        = "blog.topic";
    public static final String BLOG_CHANGED_ROUTING = "BLOG_CHANGED";
    public static final String BLOG_CHANGED_QUEUE   = "blog.changed.q";

    @Bean
    public TopicExchange blogExchange() {
        return new TopicExchange(BLOG_EXCHANGE, true, false);
    }

    @Bean
    public Queue blogChangedQueue() {
        return new Queue(BLOG_CHANGED_QUEUE, true);
    }

    @Bean
    public Binding blogChangedBinding() {
        return BindingBuilder.bind(blogChangedQueue())
                .to(blogExchange())
                .with(BLOG_CHANGED_ROUTING);
    }
}

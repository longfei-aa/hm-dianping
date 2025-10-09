package com.hmdp.mq.consumer;

import com.hmdp.entity.Blog;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mq.config.BlogMQ;
import com.hmdp.service.IBlogIndexerService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class BlogChangedListener {

    @Resource
    private BlogMapper blogMapper;
    @Resource
    private IBlogIndexerService blogIndexerService;

    @RabbitListener(queues = BlogMQ.BLOG_CHANGED_QUEUE)
    public void onBlogChanged(String payload) {
        cn.hutool.json.JSONObject j = cn.hutool.json.JSONUtil.parseObj(payload);
        Long blogId = j.getLong("blogId");
        Blog blog = blogMapper.selectById(blogId);
        if (blog == null) return;
        blogIndexerService.rebuildIndex(blog);
    }
}

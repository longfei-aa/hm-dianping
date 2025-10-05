package com.hmdp.mq.consumer;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.hmdp.service.VisionCaptionProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class AiReviewDraftConsumer implements InitializingBean {

    @Resource
    private StringRedisTemplate redis;
    @Resource
    private VisionCaptionProvider captionProvider;

    public static final String STREAM_KEY = "ai:review_draft";
    public static final String GROUP = "ai_review_group";
    public static final String CONSUMER = "c1";
    public static final String TASK_KEY_PREFIX = "ai:task:";

    @Override
    public void afterPropertiesSet() {
        try {
            // 创建消费组（已存在会抛错，这里吞掉）
            redis.opsForStream().createGroup(STREAM_KEY, ReadOffset.latest(), GROUP);
        } catch (Exception ignore) { }
    }

    @Scheduled(fixedDelay = 500) // 每 500ms 拉一次
    public void readAndHandle() {
        try {
            List<MapRecord<String, Object, Object>> msgs = redis.opsForStream().read(
                    Consumer.from(GROUP, CONSUMER),
                    StreamReadOptions.empty().count(20).block(Duration.ofMillis(2000)),
                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
            );
            if (msgs == null || msgs.isEmpty()) return;

            for (MapRecord<String, Object, Object> m : msgs) {
                handleOne(m);
            }
        } catch (Exception e) {
            log.error("AI review draft consume error", e);
        }
    }

    private void handleOne(MapRecord<String, Object, Object> m) {
        Map<Object, Object> kv = m.getValue();
        String taskId = Objects.toString(kv.get("taskId"), null);
        String style  = Objects.toString(kv.get("style"), "concise");
        String imagesJson = Objects.toString(kv.get("images"), "[]");

        try {
            JSONArray arr = JSONUtil.parseArray(imagesJson);
            List<String> images = new ArrayList<>();
            for (Object o : arr) images.add(Objects.toString(o));

            // 调用外部多模态 API 生成文案
            String draft = captionProvider.generateDraft(images, style);

            // 回写结果
            String statusKey = TASK_KEY_PREFIX + taskId + ":status";
            String draftKey  = TASK_KEY_PREFIX + taskId + ":draft";
            redis.opsForValue().set(statusKey, "done", Duration.ofMinutes(15));
            redis.opsForValue().set(draftKey, draft, Duration.ofMinutes(15));

            // ACK
            redis.opsForStream().acknowledge(STREAM_KEY, GROUP, m.getId());
        } catch (Exception ex) {
            log.error("Generate draft failed, taskId={}", taskId, ex);
            String statusKey = TASK_KEY_PREFIX + taskId + ":status";
            String errKey    = TASK_KEY_PREFIX + taskId + ":error";
            redis.opsForValue().set(statusKey, "failed", Duration.ofMinutes(10));
            redis.opsForValue().set(errKey, ex.getMessage(), Duration.ofMinutes(10));
            // 不要 ACK，保留在 pending 里便于人工/定时重试；也可以转 DLQ
        }
    }
}
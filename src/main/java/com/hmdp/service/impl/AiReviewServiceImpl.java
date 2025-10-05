package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ReviewDraftReq;
import com.hmdp.entity.ReviewDraftResp;
import com.hmdp.service.IAiReviewService;
import com.hmdp.utils.RedisIdWorker;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.STREAM_KEY;
import static com.hmdp.utils.RedisConstants.TASK_KEY_PREFIX;

/**
 * AI 看图写评价服务实现类
 */
@Service
public class AiReviewServiceImpl implements IAiReviewService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public ReviewDraftResp enqueueDraftTask(ReviewDraftReq req) {
        // 1. 参数校验
        if (req.getImageUrls() == null || req.getImageUrls().isEmpty()) {
            return new ReviewDraftResp(null, "failed", null, "imageUrls is empty");
        }
        String style = req.getStyle() == null ? "concise" : req.getStyle();

        // 2. 生成任务ID
        String taskId = String.valueOf(redisIdWorker.nextId("ai:task"));

        // 3. 组装消息体
        Map<String, String> payload = new HashMap<>();
        payload.put("taskId", taskId);
        payload.put("shopId", req.getShopId() == null ? "" : String.valueOf(req.getShopId()));
        payload.put("style", style);
        payload.put("images", JSONUtil.toJsonStr(req.getImageUrls()));

        // 4. 入队到 Redis Streams
        RecordId rid = stringRedisTemplate.opsForStream().add(StreamRecords.newRecord()
                .in(STREAM_KEY)
                .ofMap(payload));

        // 5. 写入“任务占位状态”，防止轮询时查不到
        stringRedisTemplate.opsForValue().set(TASK_KEY_PREFIX + taskId + ":status", "pending", Duration.ofMinutes(15));

        // 6. 返回任务ID
        return new ReviewDraftResp(taskId, "pending", null, null);
    }

    @Override
    public ReviewDraftResp pollDraftTask(String taskId) {
        String status = stringRedisTemplate.opsForValue().get(TASK_KEY_PREFIX + taskId + ":status");
        if (status == null) {
            // 不存在或已过期
            return new ReviewDraftResp(taskId, "pending", null, null);
        }
        if ("done".equals(status)) {
            String draft = stringRedisTemplate.opsForValue().get(TASK_KEY_PREFIX + taskId + ":draft");
            return new ReviewDraftResp(taskId, "done", draft, null);
        }
        if ("failed".equals(status)) {
            String err = stringRedisTemplate.opsForValue().get(TASK_KEY_PREFIX + taskId + ":error");
            return new ReviewDraftResp(taskId, "failed", null, err);
        }
        return new ReviewDraftResp(taskId, "pending", null, null);
    }
}
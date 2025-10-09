package com.hmdp.utils;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

public final class RedisVectorUtils {

    private RedisVectorUtils() {}

    /**
     * 用 StringRedisTemplate 写入二进制 embedding 向量到 HASH 字段
     */
    public static void hsetBinary(final StringRedisTemplate redis,
                                  final String key,
                                  final String field,
                                  final byte[] valueBytes) {

        final byte[] keyBytes   = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final byte[] fieldBytes = field.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // 显式 RedisCallback<Object>，避免类型歧义
        redis.execute((RedisCallback<Object>) connection -> {
            connection.hSet(keyBytes, fieldBytes, valueBytes);
            return null;
        });
    }
}


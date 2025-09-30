package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("outbox")
public class Outbox {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventType;
    private String aggregateId;
    private String payload;
    private Integer sent;         // 0/1
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static Outbox of(String event, String aggregateId, String payload) {
        Outbox o = new Outbox();
        o.setEventType(event);
        o.setAggregateId(aggregateId);
        o.setPayload(payload);
        o.setSent(0);
        o.setRetryCount(0);
        return o;
    }
}

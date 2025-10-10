package com.hmdp.dto;

import lombok.Data;

@Data
public class ChunkHit {
    private long blogId;
    private String title;
    private String text;
    private double sim;
    private long createdAt;
}
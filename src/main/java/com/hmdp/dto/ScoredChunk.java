package com.hmdp.dto;

import lombok.Data;

@Data
public class ScoredChunk {
    public final ChunkHit hit;
    public final double score;
}
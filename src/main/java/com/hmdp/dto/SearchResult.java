package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索结果DTO，用于返回搜索结果给前端
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {
    /** 博客ID */
    private Long blogId;

    /** 标题 */
    private String title;

    /** 命中内容预览（摘要/片段） */
    private String preview;

    /** 相关度得分（0~1 或加权综合分） */
    private Double score;

    /** 创建时间（毫秒时间戳，可选） */
    private Long createdAt;
}
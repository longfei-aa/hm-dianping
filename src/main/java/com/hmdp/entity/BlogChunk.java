package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 博客分块表（blog_chunk）
 * 每一篇 Blog 被清洗切分为多个 Chunk，用于 RAG 检索与向量索引。
 */
@Data
@Accessors(chain = true)
@TableName("tb_blog_chunk")
public class BlogChunk {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 博客 ID（外键） */
    private Long blogId;

    /** 块序号，从 0 开始 */
    private Integer ordinal;

    /** 文本内容（清洗后的正文分块） */
    private String text;

    /** 分块在原文中的起始字符位置 */
    private Integer charStart;

    /** 分块在原文中的结束字符位置 */
    private Integer charEnd;

    /** 向量版本号，例如 "bge-m3@v1" */
    private String embedVer;

    /** 向量维度，例如 1024 */
    private Integer embedDim;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}


package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.BlogChunk;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BlogChunkMapper extends BaseMapper<BlogChunk> {

    @Insert({
            "<script>",
            "INSERT INTO blog_chunk (blog_id, ordinal, text, char_start, char_end, embed_ver, embed_dim)",
            "VALUES",
            "<foreach collection='rows' item='r' separator=','>",
            "(#{r.blogId}, #{r.ordinal}, #{r.text}, #{r.charStart}, #{r.charEnd}, #{r.embedVer}, #{r.embedDim})",
            "</foreach>",
            "ON DUPLICATE KEY UPDATE",
            "  text=VALUES(text),",
            "  char_start=VALUES(char_start),",
            "  char_end=VALUES(char_end),",
            "  embed_ver=VALUES(embed_ver),",
            "  embed_dim=VALUES(embed_dim),",
            "  updated_at=CURRENT_TIMESTAMP",
            "</script>"
    })
    int upsertBatch(@Param("rows") java.util.List<BlogChunk> rows);
}


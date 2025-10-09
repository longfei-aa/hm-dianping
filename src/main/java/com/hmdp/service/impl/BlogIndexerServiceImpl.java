package com.hmdp.service.impl;


import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogChunk;
import com.hmdp.mapper.BlogChunkMapper;
import com.hmdp.service.IBlogIndexerService;
import com.hmdp.utils.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Service
public class BlogIndexerServiceImpl implements IBlogIndexerService {

    @Resource
    private BlogChunkMapper blogChunkMapper;
    @Resource
    private EmbeddingClient embeddingClient;         // 只负责请求 Python 拿向量
    @Resource
    private StringRedisTemplate redis;

    private static final String CHUNK_KEY_FMT = "blog:chunk:%d:%d";   // blog:chunk:{blogId}:{ordinal}
    private static final String DOC_KEY_FMT = "blog:doc:%d";        // blog:doc:{blogId}
    private static final String KEYSET_FMT = "blog:index:keys:%d"; // 集合记录该 blog 的所有索引 key

    @Override
    @Transactional
    public void rebuildIndex(Blog blog) {
        if (blog == null || blog.getId() == null) return;

        // 1) 本地清洗 + 切分（固定窗口，overlap）
        final String clean = HtmlCleaner.clean(blog.getContent());
        final int size = 600, overlap = 100;
        final List<TextChunker.Chunk> parts = TextChunker.split(clean, size, overlap);
        if (parts.isEmpty()) {
            // 没有有效文本：清理已有索引（可选）或直接返回
            removeFromIndex(blog.getId());
            return;
        }

        // 2) 入库 chunk（幂等：uk(blog_id, ordinal)）
        List<BlogChunk> rows = new ArrayList<BlogChunk>(parts.size());
        int ord = 0;
        for (TextChunker.Chunk c : parts) {
            BlogChunk r = new BlogChunk()
                    .setBlogId(blog.getId())
                    .setOrdinal(ord++)
                    .setText(c.getText())
                    .setCharStart(c.getStart())
                    .setCharEnd(c.getEnd())
                    .setEmbedVer("bge-m3@v1")
                    .setEmbedDim(1024);
            rows.add(r);
        }
        blogChunkMapper.upsertBatch(rows);

        // 3) 批量向量化（仅把分块文本发给 Python）
        final List<String> texts = new ArrayList<String>(rows.size());
        for (BlogChunk r : rows) texts.add(r.getText());
        final List<float[]> vecs = embeddingClient.embedBatch(texts); // Python 侧已做 normalize

        // 4) 写入 Redis：文本/属性用 StringRedisTemplate，向量用二进制 HSET
        final long blogId = blog.getId();
        final long createdAtMs = blog.getCreateTime() == null
                ? System.currentTimeMillis()
                : Timestamp.valueOf(blog.getCreateTime()).getTime();

        final String keySet = String.format(KEYSET_FMT, blogId);
        // 重建前清空集合（不直接删数据，避免误删并发写入；removeFromIndex 会精准删）
        redis.delete(keySet);

        for (int i = 0; i < rows.size(); i++) {
            BlogChunk r = rows.get(i);
            final String key = String.format(CHUNK_KEY_FMT, blogId, r.getOrdinal());
            redis.opsForSet().add(keySet, key);

            // 普通字段
            redis.opsForHash().put(key, "text", r.getText());
            redis.opsForHash().put(key, "blogId", String.valueOf(blogId));
            redis.opsForHash().put(key, "title", nvl(blog.getTitle()));
            redis.opsForHash().put(key, "images", nvl(blog.getImages()));
            redis.opsForHash().put(key, "shopId", blog.getShopId() == null ? "" : String.valueOf(blog.getShopId()));
            redis.opsForHash().put(key, "userId", blog.getUserId() == null ? "" : String.valueOf(blog.getUserId()));
            redis.opsForHash().put(key, "liked", blog.getLiked() == null ? "0" : String.valueOf(blog.getLiked()));
            redis.opsForHash().put(key, "comments", blog.getComments() == null ? "0" : String.valueOf(blog.getComments()));
            redis.opsForHash().put(key, "createdAt", String.valueOf(createdAtMs));

            // 向量字段
            if (vecs != null && i < vecs.size()) {
                byte[] bytes = VectorUtils.toFloat32LE(vecs.get(i));
                RedisVectorUtils.hsetBinary(redis, key, "embedding", bytes);
            }
        }

        // 5) 文档级（相似文章召回）：向量 = chunk 向量均值
        final String dkey = String.format(DOC_KEY_FMT, blogId);
        redis.opsForSet().add(keySet, dkey);

        // 摘要可选：如果你不需要预览展示，可以删除这行
        redis.opsForHash().put(dkey, "summary", TextChunker.summary(clean, 200));
        redis.opsForHash().put(dkey, "title", nvl(blog.getTitle()));
        redis.opsForHash().put(dkey, "images", nvl(blog.getImages()));
        redis.opsForHash().put(dkey, "shopId", blog.getShopId() == null ? "" : String.valueOf(blog.getShopId()));
        redis.opsForHash().put(dkey, "userId", blog.getUserId() == null ? "" : String.valueOf(blog.getUserId()));
        redis.opsForHash().put(dkey, "liked", blog.getLiked() == null ? "0" : String.valueOf(blog.getLiked()));
        redis.opsForHash().put(dkey, "comments", blog.getComments() == null ? "0" : String.valueOf(blog.getComments()));
        redis.opsForHash().put(dkey, "createdAt", String.valueOf(createdAtMs));

        if (vecs != null && !vecs.isEmpty()) {
            float[] doc = VectorUtils.avg(vecs); // Python 已归一化，这里均值后可不再归一化
            RedisVectorUtils.hsetBinary(redis, dkey, "embedding", VectorUtils.toFloat32LE(doc));
        }
    }

    @Override
    public void removeFromIndex(Long blogId) {
        if (blogId == null) return;
        final String keySet = String.format(KEYSET_FMT, blogId);
        java.util.Set<String> keys = redis.opsForSet().members(keySet);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        redis.delete(keySet);
        redis.delete(String.format(DOC_KEY_FMT, blogId));
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
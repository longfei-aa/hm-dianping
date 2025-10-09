package com.hmdp.service;

import com.hmdp.entity.Blog;

public interface IBlogIndexerService {

    void rebuildIndex(Blog blog);     // 清洗→切分→(入MySQL chunk)→批量Embedding→写入Redis索引

    void removeFromIndex(Long blogId);
}


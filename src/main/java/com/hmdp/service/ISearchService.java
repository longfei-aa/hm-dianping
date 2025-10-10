package com.hmdp.service;

import com.hmdp.dto.Result;

public interface ISearchService {

    /**
     * 语义搜索（自然语言 -> 相似内容）
     * @param query 搜索文本
     * @param size 返回数量
     * @return 搜索结果列表
     */
    Result semanticSearch(String query, int size);
}

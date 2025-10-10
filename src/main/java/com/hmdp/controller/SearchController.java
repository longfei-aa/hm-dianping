package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.dto.SearchReq;
import com.hmdp.dto.SearchResult;
import com.hmdp.service.ISearchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    @Resource
    private ISearchService searchService;

    /** 语义搜索：query -> 相似内容（文档粗召回 + chunk 精排） */
    @PostMapping
    public Result semantic(@RequestBody SearchReq req) {
        final String q = req == null ? null : req.getQ();
        if (q == null || q.trim().isEmpty()) return Result.fail("输入内容为空！");
        final int size = (req.getSize() == null || req.getSize() <= 0) ? 10 : Math.min(req.getSize(), 50);
        return searchService.semanticSearch(q.trim(), size);
    }
}

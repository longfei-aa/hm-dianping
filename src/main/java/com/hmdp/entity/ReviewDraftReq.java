package com.hmdp.entity;

import lombok.Data;
import java.util.List;

@Data
public class ReviewDraftReq {
    private List<String> imageUrls; // 必填：图片 URL 列表
    private String style;           // 可选：humor | formal | concise
    private Long shopId;            // 可选：用于埋点/个性化
}
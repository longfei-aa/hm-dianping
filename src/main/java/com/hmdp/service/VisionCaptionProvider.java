package com.hmdp.service;

import java.util.List;

public interface VisionCaptionProvider {
    /**
     * 读取多张图片并生成一段评价草稿，style: humor|formal|concise
     */
    String generateDraft(List<String> imageUrls, String style);
}
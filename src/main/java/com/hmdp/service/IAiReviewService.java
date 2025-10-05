package com.hmdp.service;


import com.hmdp.entity.ReviewDraftReq;
import com.hmdp.entity.ReviewDraftResp;

/**
 * AI 看图写评价服务接口
 */
public interface IAiReviewService {

    /**
     * 提交生成任务到消息队列
     * @param req 请求参数（图片URL、风格、shopId）
     * @return 任务ID + 初始状态
     */
    ReviewDraftResp enqueueDraftTask(ReviewDraftReq req);

    /**
     * 根据任务ID查询AI生成任务状态
     * @param taskId 任务ID
     * @return 任务状态 + 草稿/错误信息
     */
    ReviewDraftResp pollDraftTask(String taskId);
}
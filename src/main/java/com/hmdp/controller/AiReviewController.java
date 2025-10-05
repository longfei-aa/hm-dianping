package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.ReviewDraftReq;
import com.hmdp.entity.ReviewDraftResp;
import com.hmdp.service.IAiReviewService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/review")
public class AiReviewController {

    private IAiReviewService aiReviewService;

    @PostMapping("/draft")
    public Result draft(@RequestBody ReviewDraftReq req) {
        ReviewDraftResp resp = aiReviewService.enqueueDraftTask(req);
        return Result.ok(resp);
    }

    @GetMapping("/draft/{taskId}")
    public Result poll(@PathVariable String taskId) {
        ReviewDraftResp resp = aiReviewService.pollDraftTask(taskId);
        return Result.ok(resp);
    }
}
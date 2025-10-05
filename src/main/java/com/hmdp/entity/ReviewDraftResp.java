package com.hmdp.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReviewDraftResp {
    private String taskId;
    private String status; // pending | done | failed
    private String draft;  // done 时返回
    private String error;  // failed 时返回
}
package com.hmdp.dto;

import lombok.Data;

@Data
public class DocHit {
    private long blogId;
    private String title;
    private String summary;
    private double sim;
}

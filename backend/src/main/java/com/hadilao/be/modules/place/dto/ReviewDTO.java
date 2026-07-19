package com.hadilao.be.modules.place.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ReviewDTO {
    private String authorName;
    private Integer rating;
    private String text;
    private String relativeTimeDescription;
    private Instant publishedAt;
    private String source;
}

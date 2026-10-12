package com.example.domesticflightbookingwebbackend.dto.response.news;

import java.time.LocalDateTime;
import java.util.List;

/** News trả về client (mục 2.9 API v0.2). */
public record NewsResponse(
        Long id,
        String title,
        String content,
        String thumbnailUrl,
        String status,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        List<CategoryBrief> categories
) {
    /** Chuyên mục rút gọn kèm trong bài viết. */
    public record CategoryBrief(Long id, String name) {
    }
}

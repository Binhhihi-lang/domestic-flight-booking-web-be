package com.example.domesticflightbookingwebbackend.dto.request.news;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Payload POST/PUT /news. Nội dung HTML được sanitize trước khi lưu (chống XSS). */
public record NewsRequest(

        @NotBlank(message = "Tiêu đề không được để trống")
        @Size(max = 255, message = "Tiêu đề tối đa 255 ký tự")
        String title,

        @NotBlank(message = "Nội dung không được để trống")
        String content,

        @Size(max = 500, message = "Đường dẫn ảnh tối đa 500 ký tự")
        String thumbnailUrl,

        /** Danh mục của bài viết; id không tồn tại → 1004. */
        List<Long> categoryIds
) {
}

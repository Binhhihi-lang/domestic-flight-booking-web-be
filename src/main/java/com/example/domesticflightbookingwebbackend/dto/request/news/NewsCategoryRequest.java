package com.example.domesticflightbookingwebbackend.dto.request.news;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Payload POST/PUT /news-categories. */
public record NewsCategoryRequest(

        @NotBlank(message = "Tên chuyên mục không được để trống")
        @Size(max = 100, message = "Tên chuyên mục tối đa 100 ký tự")
        String name,

        @Size(max = 255, message = "Mô tả tối đa 255 ký tự")
        String description,

        @Pattern(regexp = "(?i)ACTIVE|INACTIVE", message = "Trạng thái chỉ nhận ACTIVE hoặc INACTIVE")
        String status
) {
}

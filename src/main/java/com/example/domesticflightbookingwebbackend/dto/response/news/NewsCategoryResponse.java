package com.example.domesticflightbookingwebbackend.dto.response.news;

/** NewsCategory trả về client (mục 2.9 API v0.2). */
public record NewsCategoryResponse(
        Long id,
        String name,
        String description,
        String status
) {
}

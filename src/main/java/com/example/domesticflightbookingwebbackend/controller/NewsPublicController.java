package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.response.news.NewsResponse;
import com.example.domesticflightbookingwebbackend.service.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * News — vùng khách (mục 2.11 API v0.4). Chỉ bài PUBLISHED; bài nháp trả 1004.
 * Quản trị ở {@code /api/admin/news}.
 */
@RestController
@RequestMapping("/api/public/news")
@RequiredArgsConstructor
public class NewsPublicController {

    private final NewsService newsService;

    @GetMapping
    public ApiResponse<PageData<NewsResponse>> list(
            ListQuery query,
            @RequestParam(name = "category_id", required = false) String categoryId) {
        return ApiResponse.success(newsService.listPublic(query, categoryId));
    }

    @GetMapping("/{id}")
    public ApiResponse<NewsResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(newsService.getPublicById(id));
    }
}
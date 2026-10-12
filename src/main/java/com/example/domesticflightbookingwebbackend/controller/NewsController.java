package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.request.news.NewsRequest;
import com.example.domesticflightbookingwebbackend.dto.response.news.NewsResponse;
import com.example.domesticflightbookingwebbackend.entity.NewsItem;
import com.example.domesticflightbookingwebbackend.service.NewsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** News — GET công khai (chỉ PUBLISHED), quản lý cần NEWS_* (mục 2.9 API v0.2). */
@RestController
@RequestMapping("/api/v1/news")
public class NewsController extends BaseCrudController<NewsItem, Long, NewsRequest, NewsResponse> {

    private final NewsService newsService;

    public NewsController(NewsService service) {
        super(service);
        this.newsService = service;
    }

    @GetMapping
    public ApiResponse<PageData<NewsResponse>> list(
            ListQuery query,
            @RequestParam(required = false) String status,
            @RequestParam(name = "category_id", required = false) String categoryId) {
        return list(query, filters("status", status, "category_id", categoryId));
    }

    /** GET detail công khai (P). */
    @Override
    @GetMapping("/{id}")
    public ApiResponse<NewsResponse> getById(@PathVariable Long id) {
        return super.getById(id);
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('NEWS_CREATE')")
    public ResponseEntity<ApiResponse<NewsResponse>> create(@Valid @RequestBody NewsRequest request) {
        return super.create(request);
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('NEWS_UPDATE')")
    public ApiResponse<NewsResponse> update(@PathVariable Long id, @Valid @RequestBody NewsRequest request) {
        return super.update(id, request);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('NEWS_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return super.delete(id);
    }

    /** Publish: PUBLISHED + published_at. Phải có ≥1 chuyên mục. */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('NEWS_PUBLISH')")
    public ApiResponse<NewsResponse> publish(@PathVariable Long id) {
        return ApiResponse.success("Xuất bản bài viết thành công", newsService.publish(id));
    }

    /** Unpublish: về DRAFT, giữ published_at cũ (NEWS-05). */
    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAuthority('NEWS_UNPUBLISH')")
    public ApiResponse<NewsResponse> unpublish(@PathVariable Long id) {
        return ApiResponse.success("Hủy xuất bản bài viết thành công", newsService.unpublish(id));
    }
}

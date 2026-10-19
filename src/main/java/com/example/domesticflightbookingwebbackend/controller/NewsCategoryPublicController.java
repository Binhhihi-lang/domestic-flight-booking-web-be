package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.service.NewsCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * NewsCategory — vùng khách (mục 2.9 API v0.4): mảng trần chỉ id + name,
 * chỉ danh mục đang hoạt động. Không lộ description/status.
 */
@RestController
@RequestMapping("/api/public/news-categories")
@RequiredArgsConstructor
public class NewsCategoryPublicController {

    private final NewsCategoryService newsCategoryService;

    @GetMapping
    public ApiResponse<List<NewsCategoryService.NewsCategoryBrief>> list() {
        return ApiResponse.success(newsCategoryService.listPublic());
    }
}
package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.request.news.NewsCategoryRequest;
import com.example.domesticflightbookingwebbackend.dto.response.news.NewsCategoryResponse;
import com.example.domesticflightbookingwebbackend.entity.NewsCategory;
import com.example.domesticflightbookingwebbackend.service.NewsCategoryService;
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

/** NewsCategory — KHÔNG public, cần NEWS_CATEGORY_* (mục 2.9 API v0.2). */
@RestController
@RequestMapping("/api/v1/news-categories")
public class NewsCategoryController
        extends BaseCrudController<NewsCategory, Long, NewsCategoryRequest, NewsCategoryResponse> {

    public NewsCategoryController(NewsCategoryService service) {
        super(service);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('NEWS_CATEGORY_READ')")
    public ApiResponse<PageData<NewsCategoryResponse>> list(ListQuery query) {
        return list(query, filters());
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('NEWS_CATEGORY_READ_DETAIL')")
    public ApiResponse<NewsCategoryResponse> getById(@PathVariable Long id) {
        return super.getById(id);
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('NEWS_CATEGORY_CREATE')")
    public ResponseEntity<ApiResponse<NewsCategoryResponse>> create(
            @Valid @RequestBody NewsCategoryRequest request) {
        return super.create(request);
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('NEWS_CATEGORY_UPDATE')")
    public ApiResponse<NewsCategoryResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody NewsCategoryRequest request) {
        return super.update(id, request);
    }

    @Override
    @PutMapping("/{id}/active")
    @PreAuthorize("hasAuthority('NEWS_CATEGORY_UPDATE')")
    public ApiResponse<NewsCategoryResponse> toggleActive(@PathVariable Long id, @RequestParam boolean value) {
        return super.toggleActive(id, value);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('NEWS_CATEGORY_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return super.delete(id);
    }
}

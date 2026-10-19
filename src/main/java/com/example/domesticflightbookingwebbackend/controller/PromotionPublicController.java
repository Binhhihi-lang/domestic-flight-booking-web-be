package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.response.promotion.PromotionResponse;
import com.example.domesticflightbookingwebbackend.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Promotion — vùng khách (mục 2.11 API v0.4): chỉ khuyến mãi đang hiệu lực. */
@RestController
@RequestMapping("/api/public/promotions")
@RequiredArgsConstructor
public class PromotionPublicController {

    private final PromotionService promotionService;

    @GetMapping
    public ApiResponse<PageData<PromotionResponse>> list(
            ListQuery query,
            @RequestParam(name = "discount_type", required = false) String discountType,
            @RequestParam(name = "applicable_trip_type", required = false) String tripType) {
        return ApiResponse.success(promotionService.listPublic(query, discountType, tripType));
    }

    @GetMapping("/{id}")
    public ApiResponse<PromotionResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(promotionService.getPublicById(id));
    }
}
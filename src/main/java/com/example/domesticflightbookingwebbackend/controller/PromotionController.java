package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.request.promotion.PromotionRequest;
import com.example.domesticflightbookingwebbackend.dto.response.promotion.PromotionResponse;
import com.example.domesticflightbookingwebbackend.entity.Promotion;
import com.example.domesticflightbookingwebbackend.service.PromotionService;
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

/** Promotion — GET công khai (chỉ đang hiệu lực), quản lý cần PROMOTION_* (mục 2.7). */
@RestController
@RequestMapping("/api/v1/promotions")
public class PromotionController extends BaseCrudController<Promotion, Long, PromotionRequest, PromotionResponse> {

    public PromotionController(PromotionService service) {
        super(service);
    }

    @GetMapping
    public ApiResponse<PageData<PromotionResponse>> list(
            ListQuery query,
            @RequestParam(name = "is_active", required = false) String isActive,
            @RequestParam(name = "discount_type", required = false) String discountType,
            @RequestParam(name = "applicable_trip_type", required = false) String tripType) {
        return list(query, filters("is_active", isActive, "discount_type", discountType,
                "applicable_trip_type", tripType));
    }

    /** GET detail công khai (P). */
    @Override
    @GetMapping("/{id}")
    public ApiResponse<PromotionResponse> getById(@PathVariable Long id) {
        return super.getById(id);
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('PROMOTION_CREATE')")
    public ResponseEntity<ApiResponse<PromotionResponse>> create(@Valid @RequestBody PromotionRequest request) {
        return super.create(request);
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PROMOTION_UPDATE')")
    public ApiResponse<PromotionResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody PromotionRequest request) {
        return super.update(id, request);
    }

    @Override
    @PutMapping("/{id}/active")
    @PreAuthorize("hasAuthority('PROMOTION_UPDATE')")
    public ApiResponse<PromotionResponse> toggleActive(@PathVariable Long id, @RequestParam boolean value) {
        return super.toggleActive(id, value);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PROMOTION_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return super.delete(id);
    }
}

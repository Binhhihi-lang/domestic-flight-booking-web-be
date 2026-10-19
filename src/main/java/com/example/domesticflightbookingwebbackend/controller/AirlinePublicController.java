package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.AirlineResponse;
import com.example.domesticflightbookingwebbackend.service.AirlineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Airline — vùng khách (mục 2.11 API v0.4). Chỉ GET, không token,
 * chỉ trả hãng đang hoạt động. Quản trị ở {@code /api/admin/airlines}.
 */
@RestController
@RequestMapping("/api/public/airlines")
@RequiredArgsConstructor
public class AirlinePublicController {

    private final AirlineService airlineService;

    @GetMapping
    public ApiResponse<PageData<AirlineResponse>> list(
            ListQuery query,
            @RequestParam(required = false) String country) {
        return ApiResponse.success(airlineService.listPublic(query, country));
    }

    @GetMapping("/{id}")
    public ApiResponse<AirlineResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(airlineService.getPublicById(id));
    }
}

package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.AirportResponse;
import com.example.domesticflightbookingwebbackend.service.AirportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Airport — vùng khách (mục 2.11 API v0.4): chỉ GET, chỉ sân bay đang hoạt động. */
@RestController
@RequestMapping("/api/public/airports")
@RequiredArgsConstructor
public class AirportPublicController {

    private final AirportService airportService;

    @GetMapping
    public ApiResponse<PageData<AirportResponse>> list(
            ListQuery query,
            @RequestParam(name = "city_id", required = false) String cityId) {
        return ApiResponse.success(airportService.listPublic(query, cityId));
    }

    @GetMapping("/{id}")
    public ApiResponse<AirportResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(airportService.getPublicById(id));
    }
}

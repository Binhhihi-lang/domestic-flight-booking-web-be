package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.CityResponse;
import com.example.domesticflightbookingwebbackend.service.CityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** City — vùng khách (mục 2.11 API v0.4): chỉ GET, chỉ thành phố đang hoạt động. */
@RestController
@RequestMapping("/api/public/cities")
@RequiredArgsConstructor
public class CityPublicController {

    private final CityService cityService;

    @GetMapping
    public ApiResponse<PageData<CityResponse>> list(
            ListQuery query,
            @RequestParam(required = false) String country) {
        return ApiResponse.success(cityService.listPublic(query, country));
    }

    @GetMapping("/{id}")
    public ApiResponse<CityResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(cityService.getPublicById(id));
    }
}

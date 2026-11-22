package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.BaggageOptionResponse;
import com.example.domesticflightbookingwebbackend.service.BaggageOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gói hành lý ký gửi công khai cho khách hàng trong luồng đặt vé ({@code GET /api/public/baggage-options}).
 */
@RestController
@RequestMapping("/api/public/baggage-options")
@RequiredArgsConstructor
public class BaggageOptionPublicController {

    private final BaggageOptionService baggageOptionService;

    @GetMapping
    public ApiResponse<PageData<BaggageOptionResponse>> list(
            ListQuery query,
            @RequestParam(name = "airline_id", required = false) Long airlineId) {
        return ApiResponse.success(baggageOptionService.listPublic(query, airlineId));
    }
}

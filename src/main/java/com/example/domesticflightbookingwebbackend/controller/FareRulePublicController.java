package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.FareRulePublicItem;
import com.example.domesticflightbookingwebbackend.service.FareRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Quy định hoàn vé công khai — vùng khách ({@code GET /api/public/fare-rules}),
 * chỉ trả version mới nhất đang hiệu lực của các hạng vé ACTIVE.
 */
@RestController
@RequestMapping("/api/public/fare-rules")
@RequiredArgsConstructor
public class FareRulePublicController {

    private final FareRuleService fareRuleService;

    @GetMapping
    public ApiResponse<List<FareRulePublicItem>> list(
            @RequestParam(name = "airline_id", required = false) Long airlineId) {
        return ApiResponse.success(fareRuleService.listPublic(airlineId));
    }
}

package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.dto.response.dashboard.OperationalDashboardResponse;
import com.example.domesticflightbookingwebbackend.dto.response.dashboard.OverviewDashboardResponse;
import com.example.domesticflightbookingwebbackend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Dashboard Vận hành (STAFF) & Dashboard Tổng quan / Báo cáo (ADMIN) (Phase 3B — B4.3, API v0.4 mục 3).
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Dashboard vận hành cho Nhân viên (STAFF) — không hiển thị doanh thu.
     */
    @GetMapping("/operational")
    @PreAuthorize("hasAuthority('DASHBOARD_READ_OPERATIONAL')")
    public ApiResponse<OperationalDashboardResponse> operational() {
        return ApiResponse.success(dashboardService.operational());
    }

    /**
     * Dashboard tổng quan & báo cáo doanh thu cho Quản trị viên (ADMIN).
     */
    @GetMapping({"", "/overview", "/reports"})
    @PreAuthorize("hasAuthority('DASHBOARD_READ')")
    public ApiResponse<OverviewDashboardResponse> overview(
            @RequestParam(name = "from_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(name = "to_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(name = "airline_id", required = false) Long airlineId,
            @RequestParam(name = "route", required = false) String route,
            @RequestParam(name = "days", required = false) Integer days) {
        return ApiResponse.success(dashboardService.overview(fromDate, toDate, airlineId, route, days));
    }
}

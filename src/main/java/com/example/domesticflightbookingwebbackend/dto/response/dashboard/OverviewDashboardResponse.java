package com.example.domesticflightbookingwebbackend.dto.response.dashboard;

import com.example.domesticflightbookingwebbackend.dto.response.booking.BookingSummaryResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dashboard tổng quan & Báo cáo thống kê cho Quản trị viên (ADMIN) — {@code GET /api/admin/dashboard}
 * (khớp cấu trúc KPI + biểu đồ doanh thu theo ngày + top tuyến bay + đơn đặt chỗ gần đây của {@code admin.html}).
 */
public record OverviewDashboardResponse(
        BigDecimal revenueToday,
        BigDecimal revenueMonth,
        BigDecimal revenueTotal,
        long bookingsToday,
        long bookingsMonth,
        long bookingsTotal,
        long paxToday,
        long paxMonth,
        long flightsToday,
        long flightsDelayedToday,
        double occupancyRatePercent,
        double cancelRatePercent,
        List<DailyRevenueItem> revenueByDay,
        List<TopRouteItem> topRoutes,
        List<BookingSummaryResponse> recentBookings
) {
    public record DailyRevenueItem(
            LocalDate date,
            String dayLabel,
            BigDecimal revenue,
            long bookingsCount
    ) {
    }

    public record TopRouteItem(
            String route,
            String departureAirportCode,
            String arrivalAirportCode,
            long bookingsCount,
            BigDecimal revenue,
            double loadFactorPercent
    ) {
    }
}

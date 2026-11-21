package com.example.domesticflightbookingwebbackend.dto.response.dashboard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Dashboard vận hành cho Nhân viên (STAFF) — {@code GET /api/admin/dashboard/operational}
 * (API v0.4 mục 3 & Nghiệp vụ 09 mục 5.8: hiện chuyến bay, ghế, đặt chỗ trong ngày;
 * <b>TUYỆT ĐỐI KHÔNG hiện doanh thu</b> cho STAFF).
 */
public record OperationalDashboardResponse(
        LocalDate date,
        long flightsToday,
        long flightsScheduled,
        long flightsBoarding,
        long flightsDelayed,
        long flightsCancelled,
        long bookingsToday,
        long bookingsPendingPayment,
        long bookingsConfirmedToday,
        long passengersToday,
        List<FlightOperationItem> todaySchedule,
        List<ActionRequiredItem> pendingActions
) {
    public record FlightOperationItem(
            Long flightId,
            String flightNumber,
            String airlineCode,
            String airlineName,
            String aircraftCode,
            String route,
            LocalDateTime scheduledDeparture,
            LocalDateTime scheduledArrival,
            String operationStatus,
            String salesStatus,
            int seatCapacity,
            long bookedSeats,
            long heldSeats,
            long availableSeats
    ) {
    }

    public record ActionRequiredItem(
            String type,
            String referenceCode,
            String title,
            String detail,
            LocalDateTime occurredAt
    ) {
    }
}

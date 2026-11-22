package com.example.domesticflightbookingwebbackend.dto.response.booking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tóm tắt một đơn đặt vé trong danh sách ({@code GET /api/bookings} và {@code GET /api/admin/bookings}).
 */
public record BookingSummaryResponse(
        Long id,
        String bookingCode,
        String orderCode,
        Long userId,
        String customerName,
        String customerEmail,
        String customerPhone,
        String tripType,
        String status,
        int passengerCount,
        BigDecimal fareAmount,
        BigDecimal promotionDiscountAmount,
        BigDecimal voucherDiscountAmount,
        BigDecimal seatSurchargeAmount,
        BigDecimal baggageAmount,
        BigDecimal totalAmount,
        String currency,
        LocalDateTime paymentExpiresAt,
        LocalDateTime cancelledAt,
        String cancelReason,
        LocalDateTime createdAt,
        List<SegmentSummary> segments
) {
    public record SegmentSummary(
            Long segmentId,
            Integer segmentOrder,
            Long flightId,
            String flightNumber,
            String airlineCode,
            String airlineName,
            String departureAirportCode,
            String departureCityName,
            String arrivalAirportCode,
            String arrivalCityName,
            LocalDateTime scheduledDeparture,
            LocalDateTime scheduledArrival,
            String fareClassCode,
            String fareClassName,
            String seatClass
    ) {
    }
}

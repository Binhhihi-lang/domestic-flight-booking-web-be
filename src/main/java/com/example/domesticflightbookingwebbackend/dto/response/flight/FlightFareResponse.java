package com.example.domesticflightbookingwebbackend.dto.response.flight;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Giá bán theo chuyến trả về cho màn quản trị {@code /api/admin/flight-fares} (API v0.4 mục 3).
 * Luôn trả đủ cả 3 mức giá {@code adult_price}, {@code child_price}, {@code infant_price}.
 */
public record FlightFareResponse(
        Long id,
        Long flightId,
        String flightNumber,
        Long fareClassId,
        String fareClassCode,
        String fareClassName,
        String seatClass,
        BigDecimal baseFare,
        BigDecimal taxFee,
        BigDecimal adultPrice,
        BigDecimal childPrice,
        BigDecimal infantPrice,
        String currency,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        LocalDateTime createdAt
) {
}

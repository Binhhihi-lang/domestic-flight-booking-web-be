package com.example.domesticflightbookingwebbackend.dto.response.booking;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Kết quả giữ ghế tạm 15 phút ({@code POST /api/bookings/{id}/seats/hold}).
 */
public record HoldSeatResponse(
        Long id,
        Long bookingId,
        Long passengerId,
        String passengerName,
        Long flightId,
        String flightNumber,
        Long flightSeatId,
        String seatCode,
        String seatClass,
        String seatTypeCode,
        String seatTypeName,
        BigDecimal surchargeAtBooking,
        String status,
        LocalDateTime holdExpiresAt,
        LocalDateTime createdAt
) {
}

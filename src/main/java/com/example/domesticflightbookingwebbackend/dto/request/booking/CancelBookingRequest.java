package com.example.domesticflightbookingwebbackend.dto.request.booking;

import jakarta.validation.constraints.Size;

/**
 * Payload POST /api/bookings/{id}/cancel — hủy đơn đặt vé (không tự động hoàn tiền).
 */
public record CancelBookingRequest(

        @Size(max = 255, message = "Lý do hủy tối đa 255 ký tự")
        String cancelReason
) {
}

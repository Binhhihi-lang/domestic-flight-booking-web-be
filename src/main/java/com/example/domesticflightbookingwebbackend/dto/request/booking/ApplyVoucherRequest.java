package com.example.domesticflightbookingwebbackend.dto.request.booking;

/**
 * Payload POST /api/bookings/{id}/voucher — áp voucher từ ví vào booking (API v0.4 mục 3:
 * nhận {@code user_voucher_id}, truyền {@code null} nếu muốn gỡ voucher khỏi booking).
 */
public record ApplyVoucherRequest(
        Long userVoucherId
) {
}

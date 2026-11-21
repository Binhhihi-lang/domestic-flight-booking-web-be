package com.example.domesticflightbookingwebbackend.dto.request.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Payload POST /api/bookings/{bookingId}/payments (Phase 3B — B4.1).
 *
 * <p>Hỗ trợ 2 phương thức thanh toán đã chốt ở QĐ-11 ({@code VNPAY} và {@code BANK_TRANSFER} / {@code VIETQR}).
 * Tham số {@code simulate_result} (tùy chọn: {@code SUCCESS} | {@code FAILED} | {@code PENDING})
 * cho phép FE/QA giả lập kết quả thanh toán ngay lập tức khi demo mà chưa cần nối cổng VNPay thật (Cụm B7).
 */
public record PaymentRequest(

        @NotBlank(message = "Phương thức thanh toán không được để trống")
        @Pattern(
                regexp = "(?i)VNPAY|BANK_TRANSFER|VIETQR",
                message = "Phương thức thanh toán chỉ nhận VNPAY, BANK_TRANSFER hoặc VIETQR")
        String paymentMethod,

        /** Tên cổng thanh toán (tùy chọn, mặc định suy ra từ paymentMethod: VNPAY / VIETQR). */
        String gatewayName,

        /**
         * Chế độ giả lập kết quả Mock Gateway:
         * - null hoặc PENDING: tạo giao dịch PENDING và trả URL/QR để tiếp tục gọi /api/payments/callback.
         * - SUCCESS: giả lập thanh toán thành công ngay lập tức (confirm booking, ghế BOOKED, xuất E-Ticket).
         * - FAILED: giả lập giao dịch thất bại (tăng retry_count, cho phép khách thử lại).
         */
        @Pattern(
                regexp = "(?i)PENDING|SUCCESS|FAILED",
                message = "Kết quả giả lập chỉ nhận PENDING, SUCCESS hoặc FAILED")
        String simulateResult
) {
}

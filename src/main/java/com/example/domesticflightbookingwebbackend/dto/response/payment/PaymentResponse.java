package com.example.domesticflightbookingwebbackend.dto.response.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Thông tin giao dịch thanh toán trả về cho client ({@code POST /api/bookings/{id}/payments},
 * {@code GET /api/payments/{id}}).
 */
public record PaymentResponse(
        Long id,
        Long bookingId,
        String bookingCode,
        String bookingStatus,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String gatewayName,
        String gatewayTransactionId,
        String status,
        Integer retryCount,
        String paymentUrl,
        VietQrInfo vietQr,
        LocalDateTime createdAt,
        LocalDateTime paidAt
) {
    /** Thông tin chuyển khoản ngân hàng VietQR (chỉ trả khi payment_method = BANK_TRANSFER / VIETQR). */
    public record VietQrInfo(
            String bankName,
            String accountNumber,
            String accountHolder,
            BigDecimal amount,
            String transferContent,
            String qrImageUrl
    ) {
    }
}

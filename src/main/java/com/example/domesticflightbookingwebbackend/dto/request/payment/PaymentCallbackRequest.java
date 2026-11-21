package com.example.domesticflightbookingwebbackend.dto.request.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * Payload POST /api/payments/callback — nhận callback/webhook từ cổng thanh toán (Mock Gateway / VNPay / VietQR).
 *
 * <p>Đảm bảo tính <b>Idempotency</b> theo cặp {@code (gateway_name, gateway_transaction_id)}:
 * nếu callback đến nhiều lần với cùng {@code gateway_transaction_id} đã xử lý thành công trước đó,
 * hệ thống bỏ qua an toàn mà không tạo giao dịch hay xuất vé điện tử trùng lặp.
 */
public record PaymentCallbackRequest(

        Long paymentId,

        Long bookingId,

        String bookingCode,

        @NotBlank(message = "Tên cổng thanh toán không được để trống")
        String gatewayName,

        @NotBlank(message = "Mã giao dịch cổng thanh toán (gateway_transaction_id) không được để trống")
        String gatewayTransactionId,

        @NotBlank(message = "Trạng thái kết quả thanh toán không được để trống")
        @Pattern(regexp = "(?i)SUCCESS|FAILED", message = "Trạng thái callback chỉ nhận SUCCESS hoặc FAILED")
        String status,

        BigDecimal amount,

        String signature,

        String failureReason
) {
}

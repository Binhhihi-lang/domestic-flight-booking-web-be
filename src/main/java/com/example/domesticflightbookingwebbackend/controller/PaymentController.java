package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.dto.request.payment.PaymentCallbackRequest;
import com.example.domesticflightbookingwebbackend.dto.request.payment.PaymentRequest;
import com.example.domesticflightbookingwebbackend.dto.response.payment.PaymentResponse;
import com.example.domesticflightbookingwebbackend.security.SecurityUtils;
import com.example.domesticflightbookingwebbackend.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thanh toán đơn đặt vé & Nhận callback từ cổng thanh toán (Phase 3B — B4.1, API v0.4 mục 3).
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/bookings/{bookingId}/payments")
    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    public ResponseEntity<ApiResponse<PaymentResponse>> create(
            @PathVariable Long bookingId,
            @Valid @RequestBody PaymentRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Khởi tạo giao dịch thanh toán thành công",
                        paymentService.create(currentUserId, bookingId, request)));
    }

    @GetMapping("/payments/{paymentId}")
    @PreAuthorize("hasAnyAuthority('PAYMENT_READ_SELF', 'BOOKING_READ')")
    public ApiResponse<PaymentResponse> getSelf(@PathVariable Long paymentId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean isStaffOrAdmin = SecurityUtils.hasAuthority("BOOKING_READ");
        return ApiResponse.success(paymentService.getSelf(currentUserId, isStaffOrAdmin, paymentId));
    }

    /**
     * Endpoint công khai cho cổng thanh toán (Mock Gateway / VNPay / VietQR) gọi callback.
     * Chống xử lý trùng lặp (Idempotent) theo {@code (gateway_name, gateway_transaction_id)}.
     */
    @PostMapping("/payments/callback")
    public ApiResponse<PaymentResponse> callback(@Valid @RequestBody PaymentCallbackRequest request) {
        return ApiResponse.success("Xử lý callback thanh toán thành công",
                paymentService.handleCallback(request));
    }
}

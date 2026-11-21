package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.dto.request.payment.PaymentCallbackRequest;
import com.example.domesticflightbookingwebbackend.dto.request.payment.PaymentRequest;
import com.example.domesticflightbookingwebbackend.dto.response.payment.PaymentResponse;
import com.example.domesticflightbookingwebbackend.security.SecurityUtils;
import com.example.domesticflightbookingwebbackend.service.PaymentService;
import com.example.domesticflightbookingwebbackend.service.PayosGatewayService;
import com.example.domesticflightbookingwebbackend.service.VnpayGatewayService;
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
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

/**
 * Thanh toán đơn đặt vé & Nhận callback từ cổng thanh toán (Phase 3B — B4.1, API v0.4 mục 3
 * + Tích hợp VNPay & payOS thật theo mẫu LaptopShop).
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final VnpayGatewayService vnpayGatewayService;
    private final PayosGatewayService payosGatewayService;

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

    // ================= VNPay Real Gateway Callbacks =================

    @GetMapping("/payments/vnpay/return")
    public ResponseEntity<Void> vnpayReturn(@RequestParam Map<String, String> params) {
        String redirectUrl = vnpayGatewayService.handleReturn(params);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }

    @RequestMapping(path = "/payments/vnpay/ipn", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, String> vnpayIpn(@RequestParam Map<String, String> params) {
        return vnpayGatewayService.handleIpn(params);
    }

    // ================= payOS Real Gateway Callbacks & Sync =================

    @GetMapping("/payments/payos/return")
    public ResponseEntity<Void> payosReturn(@RequestParam Map<String, String> params) {
        String redirectUrl = payosGatewayService.handleReturn(params);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }

    @GetMapping("/payments/payos/cancel")
    public ResponseEntity<Void> payosCancel(@RequestParam Map<String, String> params) {
        String redirectUrl = payosGatewayService.handleReturn(params);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }

    @PostMapping("/payments/payos/webhook")
    public ResponseEntity<String> payosWebhook(@RequestBody Map<String, Object> body) {
        return switch (payosGatewayService.handleWebhook(body)) {
            case ACK -> ResponseEntity.ok("OK");
            case INVALID_SIGNATURE -> ResponseEntity.badRequest().body("Invalid signature");
            case NOT_CONFIGURED -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("payOS not configured");
            case UNKNOWN_ORDER -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Order not found");
        };
    }

    @PostMapping("/payments/{paymentId}/payos/sync")
    @PreAuthorize("hasAnyAuthority('PAYMENT_READ_SELF', 'BOOKING_READ')")
    public ApiResponse<Map<String, Boolean>> payosSync(@PathVariable Long paymentId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean isStaffOrAdmin = SecurityUtils.hasAuthority("BOOKING_READ");
        boolean paid = payosGatewayService.syncPayment(currentUserId, isStaffOrAdmin, paymentId);
        return ApiResponse.success("Đồng bộ trạng thái thanh toán payOS thành công", Map.of("paid", paid));
    }
}

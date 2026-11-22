package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.request.booking.ApplyVoucherRequest;
import com.example.domesticflightbookingwebbackend.dto.request.booking.BaggageListRequest;
import com.example.domesticflightbookingwebbackend.dto.request.booking.BookingRequest;
import com.example.domesticflightbookingwebbackend.dto.request.booking.CancelBookingRequest;
import com.example.domesticflightbookingwebbackend.dto.request.booking.PassengerListRequest;
import com.example.domesticflightbookingwebbackend.dto.response.booking.BookingDetailResponse;
import com.example.domesticflightbookingwebbackend.dto.response.booking.BookingSummaryResponse;
import com.example.domesticflightbookingwebbackend.security.SecurityUtils;
import com.example.domesticflightbookingwebbackend.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Đặt vé & Quản lý đặt chỗ của khách hàng (Self-service) — luôn lấy {@code user_id} từ JWT token
 * để chống IDOR (Phase 3 — B3.3, API v0.4 mục 3).
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @PreAuthorize("hasAuthority('BOOKING_CREATE')")
    public ResponseEntity<ApiResponse<BookingDetailResponse>> create(
            @Valid @RequestBody BookingRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo đơn đặt vé thành công",
                        bookingService.create(currentUserId, request)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BOOKING_READ_SELF')")
    public ApiResponse<PageData<BookingSummaryResponse>> listSelf(
            ListQuery query,
            @RequestParam(name = "status", required = false) String status) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(bookingService.listSelf(currentUserId, query, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('BOOKING_READ_SELF')")
    public ApiResponse<BookingDetailResponse> getSelf(@PathVariable Long id) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(bookingService.getSelf(currentUserId, id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('BOOKING_CANCEL_SELF')")
    public ApiResponse<BookingDetailResponse> cancel(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) CancelBookingRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(
                "Đã hủy đơn đặt vé. Quý khách vui lòng liên hệ đại lý để được hỗ trợ hoàn tiền theo chính sách hạng vé.",
                bookingService.cancel(currentUserId, id, request));
    }

    @PostMapping("/{id}/passengers")
    @PreAuthorize("hasAuthority('PASSENGER_CREATE')")
    public ApiResponse<BookingDetailResponse> addPassengers(
            @PathVariable Long id,
            @Valid @RequestBody PassengerListRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success("Cập nhật danh sách hành khách thành công",
                bookingService.addPassengers(currentUserId, id, request));
    }

    @PostMapping("/{id}/voucher")
    @PreAuthorize("hasAuthority('VOUCHER_APPLY')")
    public ApiResponse<BookingDetailResponse> applyVoucher(
            @PathVariable Long id,
            @RequestBody(required = false) ApplyVoucherRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success("Cập nhật voucher cho đơn đặt vé thành công",
                bookingService.applyVoucher(currentUserId, id, request));
    }

    @DeleteMapping("/{id}/voucher")
    @PreAuthorize("hasAuthority('VOUCHER_APPLY')")
    public ApiResponse<BookingDetailResponse> removeVoucher(@PathVariable Long id) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success("Đã gỡ voucher khỏi đơn đặt vé",
                bookingService.applyVoucher(currentUserId, id, null));
    }

    @PostMapping("/{id}/baggages")
    @PreAuthorize("hasAuthority('BOOKING_CREATE')")
    public ApiResponse<BookingDetailResponse> addBaggages(
            @PathVariable Long id,
            @Valid @RequestBody BaggageListRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success("Cập nhật gói hành lý thành công",
                bookingService.addBaggages(currentUserId, id, request));
    }
}

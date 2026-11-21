package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.request.booking.CancelBookingRequest;
import com.example.domesticflightbookingwebbackend.dto.response.booking.BookingDetailResponse;
import com.example.domesticflightbookingwebbackend.dto.response.booking.BookingSummaryResponse;
import com.example.domesticflightbookingwebbackend.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản lý đặt chỗ toàn hệ thống — vùng quản trị ADMIN/STAFF (Phase 3 — B3.3, API v0.4 mục 3 & QĐ-13:
 * Booking là chứng từ, chỉ xem danh sách/chi tiết và hỗ trợ hủy vé, không thêm/sửa/xóa).
 */
@RestController
@RequestMapping("/api/admin/bookings")
@RequiredArgsConstructor
public class AdminBookingController {

    private final BookingService bookingService;

    @GetMapping
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    public ApiResponse<PageData<BookingSummaryResponse>> list(
            ListQuery query,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "user_id", required = false) Long userId) {
        return ApiResponse.success(bookingService.listAll(query, status, userId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    public ApiResponse<BookingDetailResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(bookingService.getById(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    public ApiResponse<BookingDetailResponse> cancel(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) CancelBookingRequest request) {
        return ApiResponse.success("Đã hủy đơn đặt vé",
                bookingService.cancelByAdmin(id, request));
    }
}

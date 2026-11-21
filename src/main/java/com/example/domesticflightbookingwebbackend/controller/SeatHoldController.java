package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.dto.request.booking.HoldSeatRequest;
import com.example.domesticflightbookingwebbackend.dto.response.booking.HoldSeatResponse;
import com.example.domesticflightbookingwebbackend.security.SecurityUtils;
import com.example.domesticflightbookingwebbackend.service.SeatHoldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Giữ ghế tạm 15 phút cho khách hàng khi đặt vé — chống trùng 3 lớp (Phase 3 — B3.1, API v0.4 mục 3).
 */
@RestController
@RequestMapping("/api/bookings/{bookingId}/seats")
@RequiredArgsConstructor
public class SeatHoldController {

    private final SeatHoldService seatHoldService;

    @PostMapping("/hold")
    @PreAuthorize("hasAuthority('BOOKING_CREATE')")
    public ApiResponse<HoldSeatResponse> hold(
            @PathVariable Long bookingId,
            @Valid @RequestBody HoldSeatRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success("Giữ ghế thành công (thời hạn 15 phút)",
                seatHoldService.hold(currentUserId, bookingId, request.passengerId(), request.flightSeatId()));
    }

    @DeleteMapping({"/hold/{seatHoldId}", "/{seatHoldId}"})
    @PreAuthorize("hasAuthority('BOOKING_CREATE')")
    public ApiResponse<Void> release(
            @PathVariable Long bookingId,
            @PathVariable Long seatHoldId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        seatHoldService.release(currentUserId, bookingId, seatHoldId);
        return ApiResponse.success("Đã nhả ghế đang giữ", null);
    }
}

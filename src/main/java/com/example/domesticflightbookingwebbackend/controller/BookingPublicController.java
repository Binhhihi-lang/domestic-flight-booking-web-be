package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.dto.response.booking.BookingDetailResponse;
import com.example.domesticflightbookingwebbackend.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tra cứu đặt chỗ công khai ({@code GET /api/public/bookings/lookup}) — khớp màn `lookup` / `lookup-result`.
 * Bắt buộc khớp cả mã đặt chỗ/đơn hàng và email người đặt để chống dò PNR trái phép.
 */
@RestController
@RequestMapping("/api/public/bookings")
@RequiredArgsConstructor
public class BookingPublicController {

    private final BookingService bookingService;

    @GetMapping("/lookup")
    public ApiResponse<BookingDetailResponse> lookup(
            @RequestParam(name = "booking_code") String bookingCode,
            @RequestParam(name = "email") String email) {
        return ApiResponse.success(bookingService.lookupPublic(bookingCode, email));
    }
}

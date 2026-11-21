package com.example.domesticflightbookingwebbackend.dto.request.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Payload POST /api/bookings/{id}/passengers — khai báo hoặc cập nhật danh sách hành khách của booking.
 */
public record PassengerListRequest(

        @NotEmpty(message = "Danh sách hành khách không được để trống")
        @Size(min = 1, max = 9, message = "Một đơn đặt vé có từ 1 đến 9 hành khách")
        @Valid
        List<BookingRequest.PassengerItem> passengers
) {
}

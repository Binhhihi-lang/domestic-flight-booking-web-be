package com.example.domesticflightbookingwebbackend.dto.request.booking;

import jakarta.validation.constraints.NotNull;

/**
 * Payload POST /api/bookings/{id}/seats/hold — giữ ghế tạm 15 phút cho một hành khách trên một chặng.
 */
public record HoldSeatRequest(

        @NotNull(message = "Hành khách không được để trống")
        Long passengerId,

        @NotNull(message = "Ghế chuyến bay không được để trống")
        Long flightSeatId
) {
}

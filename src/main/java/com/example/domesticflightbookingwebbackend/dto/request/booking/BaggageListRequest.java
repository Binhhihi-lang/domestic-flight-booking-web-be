package com.example.domesticflightbookingwebbackend.dto.request.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Payload POST /api/bookings/{id}/baggages — chọn các gói hành lý ký gửi cho hành khách trong booking.
 */
public record BaggageListRequest(

        @NotNull(message = "Danh sách hành lý không được null")
        @Valid
        List<BaggageItem> baggages
) {
    public record BaggageItem(
            @NotNull(message = "Hành khách không được để trống")
            Long passengerId,

            @NotNull(message = "Gói hành lý không được để trống")
            Long baggageOptionId,

            @NotNull(message = "Số lượng gói hành lý không được để trống")
            @Min(value = 1, message = "Số lượng gói hành lý phải lớn hơn hoặc bằng 1")
            Integer quantity
    ) {
    }
}

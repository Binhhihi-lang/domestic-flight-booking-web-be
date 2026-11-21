package com.example.domesticflightbookingwebbackend.dto.request.flight;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payload PUT /api/admin/flights/{id}/seat-inventory (Phase 2).
 *
 * <p>Nếu chuyến bay chưa có tồn ghế, backend tự sinh từ {@code aircraft_seats} của máy bay.
 * Nếu {@code seats} khác rỗng, backend áp cập nhật trạng thái (AVAILABLE/BLOCKED) và
 * {@code seat_surcharge} ghi đè theo từng ghế của chuyến bay.
 */
public record SeatInventoryRequest(

        @Valid
        List<SeatOverrideItem> seats
) {
    public record SeatOverrideItem(
            Long flightSeatId,
            Long aircraftSeatId,
            String seatCode,

            @Pattern(
                    regexp = "(?i)AVAILABLE|BLOCKED",
                    message = "Quản trị chỉ được đặt trạng thái ghế AVAILABLE hoặc BLOCKED")
            String status,

            @DecimalMin(value = "0", message = "Phụ thu ghế không được âm")
            BigDecimal seatSurcharge
    ) {
    }
}

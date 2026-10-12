package com.example.domesticflightbookingwebbackend.dto.request.masterdata;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Payload PUT /aircrafts/{id}/seat-map — gửi TOÀN BỘ sơ đồ, thay thế trong 1 transaction. */
public record SeatMapRequest(

        @NotEmpty(message = "Sơ đồ ghế không được để trống")
        @Valid
        List<SeatItem> seats
) {
    /** Một ghế vật lý: hàng + cột + loại ghế. */
    public record SeatItem(
            Integer seatRow,
            String seatColumn,
            Long seatTypeId
    ) {
    }
}

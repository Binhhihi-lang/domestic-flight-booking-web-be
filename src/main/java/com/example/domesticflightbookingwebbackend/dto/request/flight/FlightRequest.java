package com.example.domesticflightbookingwebbackend.dto.request.flight;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Payload POST/PUT /api/admin/flights (Phase 2).
 */
public record FlightRequest(

        @NotBlank(message = "Số hiệu chuyến bay không được để trống")
        @Size(max = 20, message = "Số hiệu chuyến bay tối đa 20 ký tự")
        @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "Số hiệu chuyến bay chỉ gồm chữ, số và dấu gạch ngang")
        String flightNumber,

        @NotNull(message = "Hãng hàng không không được để trống")
        Long airlineId,

        @NotNull(message = "Máy bay không được để trống")
        Long aircraftId,

        @NotNull(message = "Sân bay đi không được để trống")
        Long departureAirportId,

        @NotNull(message = "Sân bay đến không được để trống")
        Long arrivalAirportId,

        @NotNull(message = "Giờ khởi hành dự kiến không được để trống")
        LocalDateTime scheduledDeparture,

        @NotNull(message = "Giờ đến dự kiến không được để trống")
        LocalDateTime scheduledArrival,

        /** Trạng thái vận hành: DRAFT | SCHEDULED | BOARDING | DELAYED | DEPARTED | ARRIVED | CANCELLED. */
        @Pattern(
                regexp = "(?i)DRAFT|SCHEDULED|BOARDING|DELAYED|DEPARTED|ARRIVED|CANCELLED",
                message = "Trạng thái vận hành không hợp lệ")
        String operationStatus,

        /** Trạng thái mở bán: OPEN | CLOSED. */
        @Pattern(regexp = "(?i)OPEN|CLOSED", message = "Trạng thái bán chỉ nhận OPEN hoặc CLOSED")
        String salesStatus,

        /** Chuyến bay gốc nếu đây là chuyến bay bổ sung khi cầu cao (null = chuyến thường). */
        Long parentFlightId
) {
}

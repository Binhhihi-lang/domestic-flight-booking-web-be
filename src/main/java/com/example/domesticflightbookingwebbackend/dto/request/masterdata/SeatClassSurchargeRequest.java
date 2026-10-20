package com.example.domesticflightbookingwebbackend.dto.request.masterdata;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/** Payload POST/PUT /api/admin/seat-class-surcharges. */
public record SeatClassSurchargeRequest(

        @NotBlank(message = "Hạng vé không được để trống")
        @Pattern(regexp = "ECONOMY|BUSINESS|FIRST", message = "Hạng vé chỉ nhận ECONOMY, BUSINESS hoặc FIRST")
        String fromClass,

        @NotBlank(message = "Hạng ghế không được để trống")
        @Pattern(regexp = "ECONOMY|BUSINESS|FIRST", message = "Hạng ghế chỉ nhận ECONOMY, BUSINESS hoặc FIRST")
        String toClass,

        @NotNull(message = "Phụ thu không được để trống")
        @DecimalMin(value = "0", message = "Phụ thu không được âm")
        BigDecimal surcharge,

        /** ACTIVE | INACTIVE — không gửi thì mặc định ACTIVE. */
        @Pattern(regexp = "(?i)ACTIVE|INACTIVE", message = "Trạng thái chỉ nhận ACTIVE hoặc INACTIVE")
        String status
) {
}

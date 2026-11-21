package com.example.domesticflightbookingwebbackend.dto.request.flight;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payload POST/PUT /api/admin/flight-fares (Phase 2).
 * Chỉ nhập giá người lớn (base_fare + tax_fee); giá trẻ em và em bé backend tự tính.
 */
public record FlightFareRequest(

        @NotNull(message = "Chuyến bay không được để trống")
        Long flightId,

        @NotNull(message = "Hạng vé không được để trống")
        Long fareClassId,

        @NotNull(message = "Giá vé cơ bản không được để trống")
        @DecimalMin(value = "0", message = "Giá vé cơ bản không được âm")
        BigDecimal baseFare,

        @NotNull(message = "Thuế và phí không được để trống")
        @DecimalMin(value = "0", message = "Thuế và phí không được âm")
        BigDecimal taxFee,

        @Pattern(regexp = "(?i)VND", message = "Đơn vị tiền tệ chỉ hỗ trợ VND")
        String currency,

        /** Thời điểm bắt đầu hiệu lực (bỏ trống = có hiệu lực ngay). */
        LocalDateTime effectiveFrom,

        /** Thời điểm kết thúc hiệu lực (null = không giới hạn, phải > effective_from). */
        LocalDateTime effectiveTo
) {
}

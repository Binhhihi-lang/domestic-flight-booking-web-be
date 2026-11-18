package com.example.domesticflightbookingwebbackend.dto.request.masterdata;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Payload POST/PUT /seat-types. seat_type_code chỉ dùng khi tạo. */
public record SeatTypeRequest(

        @NotBlank(message = "Mã loại ghế không được để trống")
        @Size(max = 30, message = "Mã loại ghế tối đa 30 ký tự")
        String seatTypeCode,

        @NotBlank(message = "Tên loại ghế không được để trống")
        @Size(max = 100, message = "Tên loại ghế tối đa 100 ký tự")
        String seatTypeName,

        @NotBlank(message = "Khoang không được để trống")
        @Pattern(
                regexp = "ECONOMY|PREMIUM_ECONOMY|BUSINESS",
                message = "Khoang chỉ nhận ECONOMY, PREMIUM_ECONOMY hoặc BUSINESS")
        String seatClass,

        /** Mã màu #RRGGBB — bỏ trống thì FE tự dùng màu mặc định theo khoang. */
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Mã màu phải có dạng #RRGGBB")
        String colorHex,

        /** Phụ thu chọn ghế mặc định (VND); bỏ trống = 0. */
        @DecimalMin(value = "0", message = "Phụ thu không được âm")
        BigDecimal surcharge,

        /** ACTIVE | INACTIVE — không gửi thì mặc định ACTIVE. */
        @Pattern(regexp = "(?i)ACTIVE|INACTIVE", message = "Trạng thái chỉ nhận ACTIVE hoặc INACTIVE")
        String status
) {
}

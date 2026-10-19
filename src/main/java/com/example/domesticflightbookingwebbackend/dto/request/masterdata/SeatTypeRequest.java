package com.example.domesticflightbookingwebbackend.dto.request.masterdata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Payload POST/PUT /seat-types. seat_type_code chỉ dùng khi tạo. */
public record SeatTypeRequest(

        @NotBlank(message = "Mã loại ghế không được để trống")
        @Size(max = 30, message = "Mã loại ghế tối đa 30 ký tự")
        String seatTypeCode,

        @NotBlank(message = "Tên loại ghế không được để trống")
        @Size(max = 100, message = "Tên loại ghế tối đa 100 ký tự")
        String seatTypeName,

        @NotBlank(message = "Hạng ghế không được để trống")
        @Pattern(regexp = "ECONOMY|BUSINESS|FIRST", message = "Hạng ghế chỉ nhận ECONOMY, BUSINESS hoặc FIRST")
        String seatClass,

        /** Mã màu #RRGGBB — bỏ trống thì FE tự dùng màu mặc định theo hạng ghế. */
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Mã màu phải có dạng #RRGGBB")
        String colorHex,

        /** ACTIVE | INACTIVE — không gửi thì mặc định ACTIVE. */
        @Pattern(regexp = "(?i)ACTIVE|INACTIVE", message = "Trạng thái chỉ nhận ACTIVE hoặc INACTIVE")
        String status
) {
}

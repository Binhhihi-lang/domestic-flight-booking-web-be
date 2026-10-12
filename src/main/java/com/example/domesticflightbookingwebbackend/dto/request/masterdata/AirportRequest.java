package com.example.domesticflightbookingwebbackend.dto.request.masterdata;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Payload POST/PUT /airports. airport_code chỉ dùng khi tạo. */
public record AirportRequest(

        @NotBlank(message = "Mã sân bay không được để trống")
        @Size(max = 10, message = "Mã sân bay tối đa 10 ký tự")
        String airportCode,

        @NotBlank(message = "Tên sân bay không được để trống")
        @Size(max = 150, message = "Tên sân bay tối đa 150 ký tự")
        String airportName,

        @NotNull(message = "Thành phố không được để trống")
        Long cityId,

        @DecimalMin(value = "-90", message = "Vĩ độ từ -90 đến 90")
        @DecimalMax(value = "90", message = "Vĩ độ từ -90 đến 90")
        BigDecimal latitude,

        @DecimalMin(value = "-180", message = "Kinh độ từ -180 đến 180")
        @DecimalMax(value = "180", message = "Kinh độ từ -180 đến 180")
        BigDecimal longitude,

        @Pattern(regexp = "(?i)ACTIVE|INACTIVE", message = "Trạng thái chỉ nhận ACTIVE hoặc INACTIVE")
        String status
) {
}

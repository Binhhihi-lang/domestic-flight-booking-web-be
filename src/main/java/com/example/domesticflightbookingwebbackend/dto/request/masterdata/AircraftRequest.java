package com.example.domesticflightbookingwebbackend.dto.request.masterdata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Payload POST/PUT /aircrafts. aircraft_code chỉ dùng khi tạo. */
public record AircraftRequest(

        @NotBlank(message = "Mã máy bay không được để trống")
        @Size(max = 30, message = "Mã máy bay tối đa 30 ký tự")
        String aircraftCode,

        @NotBlank(message = "Dòng máy bay không được để trống")
        @Size(max = 100, message = "Dòng máy bay tối đa 100 ký tự")
        String model,

        @Size(max = 100, message = "Nhà sản xuất tối đa 100 ký tự")
        String manufacturer,

        @NotNull(message = "Sức chứa không được để trống")
        @Positive(message = "Sức chứa phải lớn hơn 0")
        Integer seatCapacity,

        @Pattern(regexp = "(?i)ACTIVE|INACTIVE", message = "Trạng thái chỉ nhận ACTIVE hoặc INACTIVE")
        String status
) {
}

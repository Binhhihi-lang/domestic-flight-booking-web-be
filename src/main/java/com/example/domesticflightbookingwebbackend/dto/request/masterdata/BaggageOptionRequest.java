package com.example.domesticflightbookingwebbackend.dto.request.masterdata;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Payload POST/PUT /admin/baggage-options. {@code airline_id} chỉ dùng khi tạo. */
public record BaggageOptionRequest(

        @NotNull(message = "Hãng áp dụng không được để trống")
        Long airlineId,

        @NotBlank(message = "Tên gói hành lý không được để trống")
        @Size(max = 100, message = "Tên gói hành lý tối đa 100 ký tự")
        String name,

        @NotNull(message = "Khối lượng không được để trống")
        @DecimalMin(value = "0", inclusive = false, message = "Khối lượng phải lớn hơn 0")
        @DecimalMax(value = "100", message = "Khối lượng tối đa 100 kg")
        BigDecimal weightKg,

        @NotNull(message = "Giá không được để trống")
        @DecimalMin(value = "0", message = "Giá không được âm")
        BigDecimal price,

        @Pattern(regexp = "(?i)ACTIVE|INACTIVE", message = "Trạng thái chỉ nhận ACTIVE hoặc INACTIVE")
        String status
) {
}

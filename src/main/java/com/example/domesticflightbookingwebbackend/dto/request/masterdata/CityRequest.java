package com.example.domesticflightbookingwebbackend.dto.request.masterdata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Payload POST/PUT /cities. city_code chỉ dùng khi tạo. */
public record CityRequest(

        @NotBlank(message = "Mã thành phố không được để trống")
        @Size(max = 10, message = "Mã thành phố tối đa 10 ký tự")
        String cityCode,

        @NotBlank(message = "Tên thành phố không được để trống")
        @Size(max = 100, message = "Tên thành phố tối đa 100 ký tự")
        String cityName,

        @NotBlank(message = "Quốc gia không được để trống")
        @Size(max = 100, message = "Quốc gia tối đa 100 ký tự")
        String country,

        @Size(max = 500, message = "Đường dẫn ảnh tối đa 500 ký tự")
        String imageUrl,

        @Pattern(regexp = "(?i)ACTIVE|INACTIVE", message = "Trạng thái chỉ nhận ACTIVE hoặc INACTIVE")
        String status
) {
}

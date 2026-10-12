package com.example.domesticflightbookingwebbackend.dto.request.masterdata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload POST/PUT /airlines.
 *
 * <p>{@code airline_code} chỉ dùng khi tạo — PUT bỏ qua (code là định danh
 * nghiệp vụ, không cho đổi; muốn đổi phải tạo mới + soft delete bản cũ).
 */
public record AirlineRequest(

        @NotBlank(message = "Mã hãng không được để trống")
        @Size(max = 10, message = "Mã hãng tối đa 10 ký tự")
        String airlineCode,

        @NotBlank(message = "Tên hãng không được để trống")
        @Size(max = 150, message = "Tên hãng tối đa 150 ký tự")
        String airlineName,

        @Size(max = 100, message = "Quốc gia tối đa 100 ký tự")
        String country,

        @Size(max = 500, message = "Đường dẫn logo tối đa 500 ký tự")
        String logoUrl,

        /** ACTIVE | INACTIVE — không gửi thì mặc định ACTIVE. */
        @Pattern(regexp = "(?i)ACTIVE|INACTIVE", message = "Trạng thái chỉ nhận ACTIVE hoặc INACTIVE")
        String status
) {
}

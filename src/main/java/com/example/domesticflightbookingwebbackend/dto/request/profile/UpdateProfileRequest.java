package com.example.domesticflightbookingwebbackend.dto.request.profile;

import jakarta.validation.constraints.Size;

/**
 * Payload PUT /profile. CHỈ 3 trường này sửa được (mục 2.2 API v0.2);
 * {@code email}, {@code status}, {@code roles} không nằm trong payload.
 */
public record UpdateProfileRequest(

        @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
        String fullName,

        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
        String phone,

        @Size(max = 500, message = "Đường dẫn ảnh tối đa 500 ký tự")
        String avatarUrl
) {
}

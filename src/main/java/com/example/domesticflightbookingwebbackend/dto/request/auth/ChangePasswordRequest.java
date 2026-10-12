package com.example.domesticflightbookingwebbackend.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload POST /auth/change-password. Sai mật khẩu hiện tại → 1007. */
public record ChangePasswordRequest(

        @NotBlank(message = "Mật khẩu hiện tại không được để trống")
        String currentPassword,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 8, max = 100, message = "Mật khẩu phải từ 8 đến 100 ký tự")
        String newPassword
) {
}

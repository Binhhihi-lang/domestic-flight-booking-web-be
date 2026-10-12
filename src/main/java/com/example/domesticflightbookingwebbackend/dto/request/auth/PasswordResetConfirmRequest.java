package com.example.domesticflightbookingwebbackend.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload POST /auth/password-reset/confirm. Token sai/hết hạn → 1006. */
public record PasswordResetConfirmRequest(

        @NotBlank(message = "Token không được để trống")
        String token,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 8, max = 100, message = "Mật khẩu phải từ 8 đến 100 ký tự")
        String newPassword
) {
}

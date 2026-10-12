package com.example.domesticflightbookingwebbackend.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload POST /auth/password-reset/request.
 * Luôn trả 200 message trung tính dù email có tồn tại hay không (chống dò tài khoản).
 */
public record PasswordResetRequest(

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        String email
) {
}

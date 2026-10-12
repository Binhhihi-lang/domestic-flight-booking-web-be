package com.example.domesticflightbookingwebbackend.dto.response.auth;

/**
 * `data` của POST /auth/password-reset/request.
 *
 * <p>MVP chưa có mail server (open item #2): trả token thẳng trong response để
 * luồng đặt lại mật khẩu test được end-to-end. Khi tích hợp dịch vụ mail, bỏ
 * trường {@code resetToken} khỏi response.
 */
public record PasswordResetResponse(String resetToken) {
}

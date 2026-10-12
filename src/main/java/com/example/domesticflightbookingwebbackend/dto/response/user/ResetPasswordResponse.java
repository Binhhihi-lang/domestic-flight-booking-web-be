package com.example.domesticflightbookingwebbackend.dto.response.user;

/**
 * `data` của POST /users/{id}/reset-password — trả mật khẩu tạm (open item #4:
 * không có mail server nên ADMIN đọc rồi gửi tay cho nhân viên).
 */
public record ResetPasswordResponse(String temporaryPassword) {
}

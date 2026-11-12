package com.example.domesticflightbookingwebbackend.service.mail;

/**
 * Gửi email giao dịch. Tách interface để nghiệp vụ (AuthService) không phụ
 * thuộc trực tiếp JavaMail — đổi nhà cung cấp (SMTP → API) chỉ thay một lớp.
 */
public interface MailService {

    /** Gửi thư đặt lại mật khẩu. Ném lỗi runtime nếu không gửi được. */
    void sendPasswordReset(MailMessage message);
}

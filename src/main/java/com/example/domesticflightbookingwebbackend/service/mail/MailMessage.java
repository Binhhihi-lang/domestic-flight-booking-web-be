package com.example.domesticflightbookingwebbackend.service.mail;

import java.time.Instant;

/**
 * Dữ liệu một thư đặt lại mật khẩu.
 *
 * @param to        địa chỉ nhận
 * @param fullName  tên hiển thị; null thì nội dung thư chào bằng email
 * @param resetLink liên kết đầy đủ (đã gồm token) người dùng bấm vào
 * @param expiresAt hạn dùng liên kết — in trong thư để người dùng biết
 */
public record MailMessage(
        String to,
        String fullName,
        String resetLink,
        Instant expiresAt
) {
}

package com.example.domesticflightbookingwebbackend.service.mail;

/**
 * Gửi email giao dịch. Tách interface để nghiệp vụ (AuthService) không phụ
 * thuộc trực tiếp JavaMail — đổi nhà cung cấp (SMTP → API) chỉ thay một lớp.
 */
public interface MailService {

    /** Gửi thư đặt lại mật khẩu. Ném lỗi runtime nếu không gửi được. */
    void sendPasswordReset(MailMessage message);

    /** Gửi thư xác nhận đặt vé thành công kèm danh sách mã vé điện tử (E-Ticket). */
    default void sendBookingConfirmation(
            String to,
            String customerName,
            String bookingCode,
            String totalAmountFormatted,
            java.util.List<String> ticketLines) {
    }
}

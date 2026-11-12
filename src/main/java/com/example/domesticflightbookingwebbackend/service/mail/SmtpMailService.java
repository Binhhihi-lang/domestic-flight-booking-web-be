package com.example.domesticflightbookingwebbackend.service.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Gửi mail qua SMTP (spring.mail.*).
 *
 * <p>Khi chưa cấu hình SMTP ({@code app.mail.enabled=false} — mặc định ở
 * profile local), chỉ ghi log liên kết thay vì ném lỗi: luồng quên mật khẩu
 * vẫn chạy được khi dev không có mail server, và token KHÔNG bị lộ ra response.
 */
@Slf4j
@Service
public class SmtpMailService implements MailService {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String fromAddress;
    private final String appName;
    private final String host;
    private final int port;

    public SmtpMailService(
            JavaMailSender mailSender,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${app.mail.from:no-reply@vinafly.local}") String fromAddress,
            @Value("${app.name:VinaFly}") String appName,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.port:25}") int port) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.fromAddress = fromAddress;
        this.appName = appName;
        this.host = host;
        this.port = port;
    }

    @Override
    public void sendPasswordReset(MailMessage message) {
        if (!enabled) {
            log.warn("[mail TẮT] Không gửi thư. Đặt MAIL_ENABLED=true trong .env để bật. "
                            + "Liên kết đặt lại mật khẩu cho {}: {} (hết hạn {})",
                    message.to(), message.resetLink(), TIME_FORMAT.format(message.expiresAt()));
            return;
        }

        log.info("Bắt đầu gửi thư đặt lại mật khẩu: to={}, from={}, qua {}:{}",
                message.to(), fromAddress, host, port);

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(fromAddress);
        mail.setTo(message.to());
        mail.setSubject("[" + appName + "] Đặt lại mật khẩu");
        mail.setText(buildBody(message));

        try {
            mailSender.send(mail);
            log.info("Đã gửi thư đặt lại mật khẩu tới {}", message.to());
        } catch (MailException e) {
            // In cả cause để thấy lỗi SMTP gốc (sai app password, chặn cổng 587…)
            log.error("Gửi thư thất bại tới {} qua {}:{} — {}",
                    message.to(), host, port, e.getMostSpecificCause().getMessage(), e);
            throw e;
        }
    }

    private String buildBody(MailMessage message) {
        String greeting = message.fullName() == null || message.fullName().isBlank()
                ? "Xin chào,"
                : "Xin chào " + message.fullName() + ",";
        return """
                %s

                Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản %s tại %s.
                Bấm liên kết dưới đây để đặt mật khẩu mới:

                %s

                Liên kết hết hạn lúc %s (15 phút kể từ khi yêu cầu).
                Nếu bạn không yêu cầu, hãy bỏ qua thư này — mật khẩu hiện tại vẫn giữ nguyên.

                Trân trọng,
                %s
                """.formatted(greeting, message.to(), appName,
                message.resetLink(), TIME_FORMAT.format(message.expiresAt()), appName);
    }
}

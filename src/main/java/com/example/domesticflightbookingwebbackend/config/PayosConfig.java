package com.example.domesticflightbookingwebbackend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình cổng thanh toán payOS (VietQR — đối chiếu mẫu LaptopShop).
 *
 * <p>3 khóa {@code PAYOS_CLIENT_ID}, {@code PAYOS_API_KEY}, {@code PAYOS_CHECKSUM_KEY}
 * lấy tại "Kênh thanh toán" trên {@code my.payos.vn}. Khi đã điền đủ 3 khóa,
 * hệ thống gọi trực tiếp {@code POST /v2/payment-requests} của payOS để sinh mã VietQR
 * kèm đối soát tự động qua Webhook và endpoint {@code /sync}. Khi để trống, hệ thống
 * tự động fallback về mẫu VietQR tĩnh để dev/test không bị chặn.
 */
@Configuration
@Getter
@Setter
public class PayosConfig {

    @Value("${payos.client-id:}")
    private String clientId;

    @Value("${payos.api-key:}")
    private String apiKey;

    @Value("${payos.checksum-key:}")
    private String checksumKey;

    @Value("${payos.base-url:https://api-merchant.payos.vn}")
    private String baseUrl = "https://api-merchant.payos.vn";

    @Value("${payos.checkout-host:https://pay.payos.vn}")
    private String checkoutHost = "https://pay.payos.vn";

    @Value("${payos.return-url:http://localhost:8080/api/payments/payos/return}")
    private String returnUrl = "http://localhost:8080/api/payments/payos/return";

    @Value("${payos.cancel-url:http://localhost:8080/api/payments/payos/cancel}")
    private String cancelUrl = "http://localhost:8080/api/payments/payos/cancel";

    @Value("${payos.fe-return-url:http://localhost:4200/payment-result}")
    private String feReturnUrl = "http://localhost:4200/payment-result";

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && checksumKey != null && !checksumKey.isBlank()
                && !"CHANGE_ME".equalsIgnoreCase(clientId)
                && !"CHANGE_ME".equalsIgnoreCase(apiKey)
                && !"CHANGE_ME".equalsIgnoreCase(checksumKey);
    }
}

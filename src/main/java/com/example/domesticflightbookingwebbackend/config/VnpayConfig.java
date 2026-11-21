package com.example.domesticflightbookingwebbackend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình cổng thanh toán VNPay (chuẩn v2.1.0, đối chiếu mẫu LaptopShop).
 *
 * <p>Khi {@link #isConfigured()} = {@code true} (đã điền {@code VNPAY_TMN_CODE} và
 * {@code VNPAY_HASH_SECRET} trong {@code .env}), hệ thống tự động ký URL thanh toán
 * chuẩn HMAC-SHA512 / SHA-256 và xác thực chữ ký ở {@code /return} & {@code /ipn}.
 * Khi để trống, hệ thống rơi về chế độ Mock Gateway cho môi trường dev/test.
 */
@Configuration
@Getter
@Setter
public class VnpayConfig {

    @Value("${vnpay.tmn-code:}")
    private String tmnCode;

    @Value("${vnpay.hash-secret:}")
    private String hashSecret;

    @Value("${vnpay.hash-type:hmacsha512}")
    private String hashType = "hmacsha512";

    @Value("${vnpay.url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String url = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";

    @Value("${vnpay.version:2.1.0}")
    private String version = "2.1.0";

    @Value("${vnpay.currency:VND}")
    private String currency = "VND";

    @Value("${vnpay.locale:vn}")
    private String locale = "vn";

    @Value("${vnpay.order-type:other}")
    private String orderType = "other";

    @Value("${vnpay.return-url:http://localhost:8080/api/payments/vnpay/return}")
    private String returnUrl = "http://localhost:8080/api/payments/vnpay/return";

    @Value("${vnpay.ipn-url:http://localhost:8080/api/payments/vnpay/ipn}")
    private String ipnUrl = "http://localhost:8080/api/payments/vnpay/ipn";

    @Value("${vnpay.fe-return-url:http://localhost:4200/payment-result}")
    private String feReturnUrl = "http://localhost:4200/payment-result";

    public boolean isConfigured() {
        return tmnCode != null && !tmnCode.isBlank()
                && hashSecret != null && !hashSecret.isBlank()
                && !"CHANGE_ME".equalsIgnoreCase(tmnCode)
                && !"CHANGE_ME".equalsIgnoreCase(hashSecret);
    }
}

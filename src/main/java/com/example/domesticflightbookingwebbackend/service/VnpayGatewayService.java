package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.config.VnpayConfig;
import com.example.domesticflightbookingwebbackend.dto.request.payment.PaymentCallbackRequest;
import com.example.domesticflightbookingwebbackend.entity.Booking;
import com.example.domesticflightbookingwebbackend.entity.Payment;
import com.example.domesticflightbookingwebbackend.entity.PaymentStatus;
import com.example.domesticflightbookingwebbackend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dịch vụ cổng thanh toán VNPay chuẩn v2.1.0 (tích hợp theo mẫu LaptopShop).
 *
 * <ul>
 *   <li>Ký URL thanh toán bằng {@code HmacSHA512} (mặc định) hoặc {@code SHA-256} theo
 *       {@code vnpay.hash-type}.</li>
 *   <li>Mã tham chiếu {@code vnp_TxnRef} định dạng {@code <bookingCode>-<paymentId>} để mỗi
 *       lần thử lại (retry) có mã duy nhất trên VNPay và tra ngược chính xác bản ghi {@link Payment}.</li>
 *   <li>Hỗ trợ cả callback trình duyệt ({@code GET /api/payments/vnpay/return}) lẫn IPN
 *       server-to-server ({@code GET/POST /api/payments/vnpay/ipn}).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VnpayGatewayService {

    static final String HMAC_SHA512 = "HmacSHA512";
    static final String SHA_256 = "SHA-256";
    static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final VnpayConfig config;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    public boolean isConfigured() {
        return config.isConfigured();
    }

    /**
     * Dựng URL chuyển hướng sang cổng VNPay. Nếu đã cấu hình {@code VNPAY_TMN_CODE} và
     * {@code VNPAY_HASH_SECRET} thì ký đầy đủ {@code vnp_SecureHash}; nếu chưa cấu hình
     * thì trả URL sandbox giả lập để dev/test không bị chặn.
     */
    public String buildPaymentUrl(Payment payment, String ipAddr) {
        Booking booking = payment.getBooking();
        String txnRef = buildTxnRef(booking, payment);
        long vnpAmount = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValue();

        if (!config.isConfigured()) {
            return config.getUrl() + "?vnp_TxnRef=" + txnRef + "&vnp_Amount=" + vnpAmount;
        }

        LocalDateTime now = LocalDateTime.now();
        Map<String, String> vnpParams = new TreeMap<>();
        vnpParams.put("vnp_Version", config.getVersion());
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", config.getTmnCode());
        vnpParams.put("vnp_Amount", String.valueOf(vnpAmount));
        vnpParams.put("vnp_CurrCode", config.getCurrency());
        vnpParams.put("vnp_TxnRef", txnRef);
        vnpParams.put("vnp_OrderInfo", "VinaFly - Thanh toan dat ve " + booking.getBookingCode());
        vnpParams.put("vnp_OrderType", config.getOrderType());
        vnpParams.put("vnp_Locale", config.getLocale());
        vnpParams.put("vnp_IpAddr", (ipAddr == null || ipAddr.isBlank()) ? "127.0.0.1" : ipAddr);
        vnpParams.put("vnp_CreateDate", now.format(TS_FORMAT));
        vnpParams.put("vnp_ExpireDate", now.plusMinutes(15).format(TS_FORMAT));
        vnpParams.put("vnp_ReturnUrl", config.getReturnUrl());

        String hashData = hashData(vnpParams);
        String secureHash = sign(hashData, config.getHashType());
        return config.getUrl() + "?" + hashData + "&vnp_SecureHash=" + secureHash;
    }

    /**
     * Xác minh chữ ký {@code vnp_SecureHash} từ callback VNPay.
     */
    public boolean verifySignature(Map<String, String> params) {
        String received = params.get("vnp_SecureHash");
        if (received == null || received.isBlank() || !config.isConfigured()) {
            return false;
        }
        String hashType = params.getOrDefault("vnp_SecureHashType", config.getHashType());
        return sign(hashData(params), hashType).equalsIgnoreCase(received);
    }

    /**
     * Xử lý {@code GET /api/payments/vnpay/return} khi VNPay redirect trình duyệt khách về.
     */
    @Transactional
    public String handleReturn(Map<String, String> params) {
        if (!verifySignature(params)) {
            return failureRedirect("invalid_signature");
        }
        Payment payment = findPaymentByTxnRef(params.get("vnp_TxnRef"));
        if (payment == null) {
            return failureRedirect("order_not_found");
        }
        applyVnpayCallback(payment, params);
        String responseCode = params.get("vnp_ResponseCode");
        String status = "00".equals(responseCode) ? "success" : "failed";
        return config.getFeReturnUrl()
                + "?bookingCode=" + encode(payment.getBooking().getBookingCode())
                + "&bookingId=" + payment.getBooking().getId()
                + "&method=vnpay"
                + "&status=" + status
                + "&code=" + encode(responseCode);
    }

    /**
     * Xử lý {@code GET/POST /api/payments/vnpay/ipn} (server VNPay gọi xác nhận).
     */
    @Transactional
    public Map<String, String> handleIpn(Map<String, String> params) {
        if (!verifySignature(params)) {
            return ipnResponse("97", "Invalid Signature");
        }
        Payment payment = findPaymentByTxnRef(params.get("vnp_TxnRef"));
        if (payment == null) {
            return ipnResponse("01", "Order not found");
        }
        long vnpAmount;
        try {
            vnpAmount = Long.parseLong(params.getOrDefault("vnp_Amount", ""));
        } catch (NumberFormatException e) {
            vnpAmount = -1L;
        }
        long expectedVnpAmount = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
        if (expectedVnpAmount != vnpAmount) {
            return ipnResponse("04", "Invalid amount");
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.FAILED) {
            return ipnResponse("02", "Order already confirmed");
        }
        applyVnpayCallback(payment, params);
        return ipnResponse("00", "Confirm Success");
    }

    private void applyVnpayCallback(Payment payment, Map<String, String> params) {
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return;
        }
        String responseCode = params.get("vnp_ResponseCode");
        boolean success = "00".equals(responseCode);
        String gatewayTxnId = params.getOrDefault("vnp_TransactionNo", "");
        if (gatewayTxnId == null || gatewayTxnId.isBlank() || "0".equals(gatewayTxnId)) {
            gatewayTxnId = "VNP-" + params.getOrDefault("vnp_TxnRef", String.valueOf(payment.getId()));
        }
        paymentService.handleCallback(new PaymentCallbackRequest(
                payment.getId(),
                payment.getBooking().getId(),
                payment.getBooking().getBookingCode(),
                "VNPAY",
                gatewayTxnId,
                success ? "SUCCESS" : "FAILED",
                payment.getAmount(),
                params.get("vnp_SecureHash"),
                success ? null : "VNPay response code: " + responseCode
        ));
    }

    private Payment findPaymentByTxnRef(String txnRef) {
        if (txnRef == null || txnRef.isBlank()) {
            return null;
        }
        int dashIdx = txnRef.lastIndexOf('-');
        String idPart = dashIdx >= 0 ? txnRef.substring(dashIdx + 1) : txnRef;
        try {
            Long paymentId = Long.parseLong(idPart);
            return paymentRepository.findWithBookingById(paymentId).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String buildTxnRef(Booking booking, Payment payment) {
        return booking.getBookingCode() + "-" + payment.getId();
    }

    private String hashData(Map<String, String> params) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            if ("vnp_SecureHash".equals(key) || "vnp_SecureHashType".equals(key)) {
                continue;
            }
            String value = params.get(key);
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.US_ASCII));
        }
        return sb.toString();
    }

    public String sign(String hashData, String hashType) {
        String secret = config.getHashSecret();
        try {
            if ("hmacsha512".equalsIgnoreCase(hashType)) {
                Mac mac = Mac.getInstance(HMAC_SHA512);
                mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA512));
                return toHex(mac.doFinal(hashData.getBytes(StandardCharsets.UTF_8)));
            }
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            return toHex(digest.digest((secret + hashData).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Lỗi khi mã hóa chữ ký VNPay", e);
        }
    }

    private String failureRedirect(String code) {
        return config.getFeReturnUrl() + "?method=vnpay&status=failed&code=" + encode(code);
    }

    private static String encode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static Map<String, String> ipnResponse(String rspCode, String message) {
        return Map.of("RspCode", rspCode, "Message", message);
    }
}

package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.config.PayosConfig;
import com.example.domesticflightbookingwebbackend.dto.request.payment.PaymentCallbackRequest;
import com.example.domesticflightbookingwebbackend.dto.response.payment.PaymentResponse;
import com.example.domesticflightbookingwebbackend.entity.Booking;
import com.example.domesticflightbookingwebbackend.entity.BookingStatus;
import com.example.domesticflightbookingwebbackend.entity.Payment;
import com.example.domesticflightbookingwebbackend.entity.PaymentStatus;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dịch vụ cổng thanh toán payOS (VietQR — tích hợp theo mẫu LaptopShop).
 *
 * <ul>
 *   <li>Tạo link thanh toán: {@code POST /v2/payment-requests} với {@code orderCode = payment.id}
 *       (số nguyên duy nhất cho từng lần thử thanh toán), ký {@code HMAC_SHA256} trên 5 trường
 *       {@code amount, cancelUrl, description, orderCode, returnUrl}.</li>
 *   <li>Webhook ({@code POST /api/payments/payos/webhook}): kiểm tra payload test của payOS,
 *       xác thực chữ ký {@code HMAC_SHA256} bằng hằng thời gian ({@link MessageDigest#isEqual})
 *       TRƯỚC khi truy vấn DB, đối chiếu số tiền và gọi {@link PaymentService#handleCallback}.</li>
 *   <li>Đồng bộ chủ động ({@code POST /api/payments/{paymentId}/payos/sync} & {@code GET /return}):
 *       gọi {@code GET /v2/payment-requests/{orderCode}} lên payOS để xác nhận thanh toán ngay cả
 *       khi chạy local chưa có ngrok/webhook.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayosGatewayService {

    static final String SUCCESS = "00";
    static final String ORDER_EXIST = "231";
    static final String HMAC_SHA256 = "HmacSHA256";

    public enum WebhookResult {
        ACK,
        INVALID_SIGNATURE,
        NOT_CONFIGURED,
        UNKNOWN_ORDER
    }

    private final PayosConfig config;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    public boolean isConfigured() {
        return config.isConfigured();
    }

    /**
     * Gọi payOS tạo payment link cho bản ghi {@link Payment}. Trả về {@link PaymentResponse.VietQrInfo}
     * chứa chuỗi {@code qrCodeRaw}, {@code checkoutUrl}, số tài khoản và chủ tài khoản nhận.
     */
    @SuppressWarnings("unchecked")
    public PaymentResponse.VietQrInfo createPaymentLink(Payment payment) {
        if (!config.isConfigured()) {
            return null;
        }
        Booking booking = payment.getBooking();
        long orderCode = payment.getId();
        long amount = payment.getAmount().longValue();
        String description = "VINAFLY " + booking.getBookingCode();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderCode", orderCode);
        payload.put("amount", amount);
        payload.put("description", description);
        payload.put("cancelUrl", config.getCancelUrl());
        payload.put("returnUrl", config.getReturnUrl());
        payload.put("signature", signCreateRequest(
                amount, config.getCancelUrl(), description, orderCode, config.getReturnUrl()));

        Map<String, Object> res = call(HttpMethod.POST, "/v2/payment-requests", payload);
        String code = str(res.get("code"));
        Map<String, Object> data;
        if (ORDER_EXIST.equals(code)) {
            data = fetchLink(orderCode);
            if (data == null) {
                throw new BusinessException("Không thể lấy lại link thanh toán payOS cho giao dịch #" + orderCode);
            }
        } else if (!SUCCESS.equals(code)) {
            log.warn("payOS tạo link thất bại: code={}, desc={}", code, res.get("desc"));
            throw new BusinessException("Không tạo được link thanh toán payOS: " + res.get("desc"));
        } else {
            data = (Map<String, Object>) res.get("data");
            if (data == null || !verifyDataSignature(data, str(res.get("signature")))) {
                log.warn("payOS trả chữ ký dữ liệu không hợp lệ khi tạo link");
                throw new BusinessException("Chữ ký phản hồi từ payOS không hợp lệ");
            }
        }

        String paymentLinkId = str(data.get("paymentLinkId"));
        String checkoutUrl = str(data.get("checkoutUrl"));
        if ((checkoutUrl == null || checkoutUrl.isBlank()) && paymentLinkId != null) {
            checkoutUrl = config.getCheckoutHost() + "/web/" + paymentLinkId;
        }
        String qrCodeRaw = str(data.get("qrCode"));
        String bin = str(data.get("bin"));
        String accountNumber = str(data.get("accountNumber"));
        String accountName = str(data.get("accountName"));
        String transferDesc = str(data.get("description"));
        if (transferDesc == null || transferDesc.isBlank()) {
            transferDesc = description;
        }
        String qrImageUrl = (bin != null && accountNumber != null)
                ? "https://img.vietqr.io/image/" + bin + "-" + accountNumber
                        + "-compact2.png?amount=" + amount
                        + "&addInfo=" + URLEncoder.encode(transferDesc, StandardCharsets.UTF_8)
                : null;

        return new PaymentResponse.VietQrInfo(
                bin != null ? "Ngân hàng (BIN " + bin + ")" : "VietQR (payOS)",
                accountNumber,
                accountName,
                payment.getAmount(),
                transferDesc,
                qrImageUrl,
                qrCodeRaw,
                checkoutUrl
        );
    }

    /**
     * Xử lý Webhook từ server payOS ({@code POST /api/payments/payos/webhook}).
     */
    @Transactional
    public WebhookResult handleWebhook(Map<String, Object> body) {
        if (!config.isConfigured()) {
            return WebhookResult.NOT_CONFIGURED;
        }
        if (isTestWebhook(body)) {
            return WebhookResult.ACK;
        }
        Object dataObj = body.get("data");
        if (!(dataObj instanceof Map<?, ?>)) {
            return WebhookResult.INVALID_SIGNATURE;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) dataObj;
        if (!verifyDataSignature(data, str(body.get("signature")))) {
            return WebhookResult.INVALID_SIGNATURE;
        }
        if (!SUCCESS.equals(str(body.get("code")))) {
            return WebhookResult.ACK;
        }

        long orderCode = asLong(data.get("orderCode"));
        Payment payment = paymentRepository.findWithBookingById(orderCode).orElse(null);
        if (payment == null) {
            log.warn("Webhook payOS cho paymentId không tồn tại: orderCode={}", orderCode);
            return WebhookResult.UNKNOWN_ORDER;
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS
                || payment.getBooking().getStatus() == BookingStatus.CONFIRMED) {
            return WebhookResult.ACK;
        }

        long amount = asLong(data.get("amount"));
        long expectedAmount = payment.getAmount().longValue();
        if (amount < expectedAmount) {
            log.warn("payOS báo chuyển thiếu cho booking {}: nhận {}, cần {}",
                    payment.getBooking().getBookingCode(), amount, expectedAmount);
            return WebhookResult.ACK;
        }

        String reference = str(data.get("reference"));
        if (reference == null || reference.isBlank()) {
            reference = "PAYOS-" + orderCode;
        }

        paymentService.handleCallback(new PaymentCallbackRequest(
                payment.getId(),
                payment.getBooking().getId(),
                payment.getBooking().getBookingCode(),
                "PAYOS",
                reference,
                "SUCCESS",
                payment.getAmount(),
                str(body.get("signature")),
                null
        ));
        return WebhookResult.ACK;
    }

    /**
     * Xử lý khi trình duyệt khách redirect về {@code /api/payments/payos/return} hoặc {@code /cancel}.
     */
    @Transactional
    public String handleReturn(Map<String, String> params) {
        Long orderCode = parseLong(params.get("orderCode"));
        if (orderCode == null) {
            return failureRedirect("order_not_found");
        }
        Payment payment = paymentRepository.findWithBookingById(orderCode).orElse(null);
        if (payment == null) {
            return failureRedirect("order_not_found");
        }
        if (payment.getStatus() != PaymentStatus.SUCCESS && config.isConfigured()) {
            syncFromPayosLink(payment);
            payment = paymentRepository.findWithBookingById(orderCode).orElse(payment);
        }
        boolean paid = payment.getStatus() == PaymentStatus.SUCCESS
                || payment.getBooking().getStatus() == BookingStatus.CONFIRMED;
        return config.getFeReturnUrl()
                + "?bookingCode=" + encode(payment.getBooking().getBookingCode())
                + "&bookingId=" + payment.getBooking().getId()
                + "&method=payos"
                + "&status=" + (paid ? "success" : "failed")
                + "&code=" + encode(params.get("code"));
    }

    /**
     * Chủ động đồng bộ trạng thái thanh toán từ payOS ({@code POST /api/payments/{paymentId}/payos/sync}).
     */
    @Transactional
    public boolean syncPayment(Long userId, boolean isStaffOrAdmin, Long paymentId) {
        Payment payment = paymentRepository.findWithBookingById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Giao dịch thanh toán không tồn tại"));
        if (!isStaffOrAdmin && !payment.getBooking().getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền đồng bộ giao dịch thanh toán này");
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS
                || payment.getBooking().getStatus() == BookingStatus.CONFIRMED) {
            return true;
        }
        if (config.isConfigured()) {
            syncFromPayosLink(payment);
        }
        return paymentRepository.findWithBookingById(paymentId)
                .map(p -> p.getStatus() == PaymentStatus.SUCCESS
                        || p.getBooking().getStatus() == BookingStatus.CONFIRMED)
                .orElse(false);
    }

    private void syncFromPayosLink(Payment payment) {
        Map<String, Object> data = fetchLink(payment.getId());
        if (data == null || !"PAID".equals(str(data.get("status")))) {
            return;
        }
        long amountPaid = asLong(data.get("amountPaid"));
        if (amountPaid < payment.getAmount().longValue()) {
            return;
        }
        String reference = "PAYOS-" + payment.getId();
        Object transactions = data.get("transactions");
        if (transactions instanceof List<?> list && !list.isEmpty()
                && list.get(0) instanceof Map<?, ?> first) {
            String ref = str(first.get("reference"));
            if (ref != null && !ref.isBlank()) {
                reference = ref;
            }
        }
        paymentService.handleCallback(new PaymentCallbackRequest(
                payment.getId(),
                payment.getBooking().getId(),
                payment.getBooking().getBookingCode(),
                "PAYOS",
                reference,
                "SUCCESS",
                payment.getAmount(),
                "PAYOS_SYNC",
                null
        ));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchLink(long orderCode) {
        try {
            Map<String, Object> res = call(HttpMethod.GET, "/v2/payment-requests/" + orderCode, null);
            if (!SUCCESS.equals(str(res.get("code")))) {
                return null;
            }
            return (Map<String, Object>) res.get("data");
        } catch (RuntimeException e) {
            log.warn("payOS không lấy được thông tin link {}: {}", orderCode, e.getMessage());
            return null;
        }
    }

    /** Payload test của payOS khi bấm xác nhận Webhook trên dashboard my.payos.vn. */
    public static boolean isTestWebhook(Map<String, Object> body) {
        if ("Giao dich thu nghiem".equals(str(body.get("desc")))) {
            return true;
        }
        Object dataObj = body.get("data");
        if (dataObj instanceof Map<?, ?> data) {
            return "VQRIO123".equals(str(data.get("description")))
                    || "MA_GIAO_DICH_THU_NGHIEM".equals(str(data.get("reference")));
        }
        return false;
    }

    public String signCreateRequest(long amount, String cancelUrl, String description,
                                    long orderCode, String returnUrl) {
        String raw = "amount=" + amount
                + "&cancelUrl=" + cancelUrl
                + "&description=" + description
                + "&orderCode=" + orderCode
                + "&returnUrl=" + returnUrl;
        return hmacSha256(raw);
    }

    public String signData(Map<String, Object> data) {
        TreeMap<String, Object> sorted = new TreeMap<>(data);
        List<String> parts = new ArrayList<>(sorted.size());
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            parts.add(entry.getKey() + "=" + normalize(entry.getValue()));
        }
        return hmacSha256(String.join("&", parts));
    }

    public boolean verifyDataSignature(Map<String, Object> data, String received) {
        if (received == null || received.isBlank() || !config.isConfigured()) {
            return false;
        }
        String expected = signData(data);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8));
    }

    private String normalize(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return objectMapper.writeValueAsString(sortDeep(value));
        }
        String text = String.valueOf(value);
        if ("null".equals(text) || "undefined".equals(text)) {
            return "";
        }
        return text;
    }

    private Object sortDeep(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((k, v) -> sorted.put(String.valueOf(k), sortDeep(v)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> sorted = new ArrayList<>(list.size());
            list.forEach(item -> sorted.add(sortDeep(item)));
            return sorted;
        }
        return value;
    }

    private String hmacSha256(String raw) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(
                    config.getChecksumKey().getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA256));
            return toHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Lỗi khi mã hóa chữ ký payOS", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(HttpMethod method, String path, Object payload) {
        RestClient.RequestBodySpec spec = restClient.method(method)
                .uri(config.getBaseUrl() + path)
                .header("x-client-id", config.getClientId())
                .header("x-api-key", config.getApiKey())
                .contentType(MediaType.APPLICATION_JSON);
        if (payload != null) {
            spec.body(objectMapper.writeValueAsString(payload));
        }
        try {
            String body = spec.retrieve().body(String.class);
            return readJson(body);
        } catch (RestClientResponseException e) {
            Map<String, Object> parsed = readJson(e.getResponseBodyAsString());
            if (parsed == null || parsed.isEmpty()) {
                throw new BusinessException("Không thể kết nối tới cổng thanh toán payOS");
            }
            return parsed;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(body, Map.class);
    }

    private String failureRedirect(String code) {
        return config.getFeReturnUrl() + "?method=payos&status=failed&code=" + encode(code);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(str(value));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
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
}

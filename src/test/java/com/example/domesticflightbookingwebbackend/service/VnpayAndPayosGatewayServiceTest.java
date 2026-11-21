package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.config.PayosConfig;
import com.example.domesticflightbookingwebbackend.config.VnpayConfig;
import com.example.domesticflightbookingwebbackend.dto.request.payment.PaymentCallbackRequest;
import com.example.domesticflightbookingwebbackend.entity.Booking;
import com.example.domesticflightbookingwebbackend.entity.BookingStatus;
import com.example.domesticflightbookingwebbackend.entity.Payment;
import com.example.domesticflightbookingwebbackend.entity.PaymentStatus;
import com.example.domesticflightbookingwebbackend.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho tích hợp cổng thanh toán thật VNPay ({@link VnpayGatewayService})
 * và payOS ({@link PayosGatewayService}) — đối chiếu chuẩn chữ ký LaptopShop.
 */
@ExtendWith(MockitoExtension.class)
class VnpayAndPayosGatewayServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentService paymentService;

    private VnpayConfig vnpayConfig;
    private VnpayGatewayService vnpayGatewayService;

    private PayosConfig payosConfig;
    private PayosGatewayService payosGatewayService;

    @BeforeEach
    void setUp() {
        vnpayConfig = new VnpayConfig();
        vnpayConfig.setTmnCode("VINAFLY01");
        vnpayConfig.setHashSecret("VINAFLY_SECRET_KEY_FOR_HMAC512_TEST");
        vnpayConfig.setHashType("hmacsha512");
        vnpayConfig.setUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        vnpayConfig.setReturnUrl("http://localhost:8080/api/payments/vnpay/return");
        vnpayConfig.setIpnUrl("http://localhost:8080/api/payments/vnpay/ipn");
        vnpayConfig.setFeReturnUrl("http://localhost:4200/payment-result");
        vnpayGatewayService = new VnpayGatewayService(vnpayConfig, paymentRepository, paymentService);

        payosConfig = new PayosConfig();
        payosConfig.setClientId("payos-client-id");
        payosConfig.setApiKey("payos-api-key");
        payosConfig.setChecksumKey("payos-checksum-secret-key");
        payosConfig.setReturnUrl("http://localhost:8080/api/payments/payos/return");
        payosConfig.setCancelUrl("http://localhost:8080/api/payments/payos/cancel");
        payosConfig.setFeReturnUrl("http://localhost:4200/payment-result");
        payosGatewayService = new PayosGatewayService(payosConfig, paymentRepository, paymentService);
    }

    @Test
    void vnpay_buildPaymentUrl_signsWithHmacSha512AndIncludesAmountX100() {
        Payment payment = samplePayment(25L, "VF900123", new BigDecimal("1500000"));

        String url = vnpayGatewayService.buildPaymentUrl(payment, "127.0.0.1");

        assertThat(url).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(url).contains("vnp_TmnCode=VINAFLY01");
        assertThat(url).contains("vnp_Amount=150000000");
        assertThat(url).contains("vnp_TxnRef=VF900123-25");
        assertThat(url).contains("vnp_SecureHash=");
    }

    @Test
    void vnpay_handleIpn_verifiesSignatureAndConfirmsPayment() {
        Payment payment = samplePayment(25L, "VF900123", new BigDecimal("1500000"));
        when(paymentRepository.findWithBookingById(25L)).thenReturn(Optional.of(payment));

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", "150000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionNo", "14592817");
        params.put("vnp_TxnRef", "VF900123-25");
        String hashData = "vnp_Amount=150000000&vnp_ResponseCode=00&vnp_TransactionNo=14592817&vnp_TxnRef=VF900123-25";
        params.put("vnp_SecureHash", vnpayGatewayService.sign(hashData, "hmacsha512"));

        Map<String, String> response = vnpayGatewayService.handleIpn(params);

        assertThat(response.get("RspCode")).isEqualTo("00");
        ArgumentCaptor<PaymentCallbackRequest> captor = ArgumentCaptor.forClass(PaymentCallbackRequest.class);
        verify(paymentService).handleCallback(captor.capture());
        assertThat(captor.getValue().gatewayName()).isEqualTo("VNPAY");
        assertThat(captor.getValue().gatewayTransactionId()).isEqualTo("14592817");
        assertThat(captor.getValue().status()).isEqualTo("SUCCESS");
    }

    @Test
    void vnpay_handleIpn_rejectsTamperedSignature() {
        Map<String, String> params = Map.of(
                "vnp_Amount", "150000000",
                "vnp_ResponseCode", "00",
                "vnp_TxnRef", "VF900123-25",
                "vnp_SecureHash", "tampered_hash_value"
        );

        Map<String, String> response = vnpayGatewayService.handleIpn(params);

        assertThat(response.get("RspCode")).isEqualTo("97");
        verify(paymentService, never()).handleCallback(any());
    }

    @Test
    void payos_handleWebhook_acknowledgesTestPayloadWithoutTouchingDb() {
        Map<String, Object> testPayload = Map.of(
                "code", "00",
                "desc", "Giao dich thu nghiem",
                "data", Map.of("description", "VQRIO123", "amount", 2000)
        );

        PayosGatewayService.WebhookResult result = payosGatewayService.handleWebhook(testPayload);

        assertThat(result).isEqualTo(PayosGatewayService.WebhookResult.ACK);
        verify(paymentRepository, never()).findWithBookingById(any());
        verify(paymentService, never()).handleCallback(any());
    }

    @Test
    void payos_handleWebhook_verifiesHmacSha256AndConfirmsBooking() {
        Payment payment = samplePayment(42L, "VF778899", new BigDecimal("2150000"));
        when(paymentRepository.findWithBookingById(42L)).thenReturn(Optional.of(payment));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderCode", 42L);
        data.put("amount", 2150000L);
        data.put("description", "VINAFLY VF778899");
        data.put("reference", "FT261121998877");
        String signature = payosGatewayService.signData(data);

        Map<String, Object> body = Map.of(
                "code", "00",
                "desc", "success",
                "data", data,
                "signature", signature
        );

        PayosGatewayService.WebhookResult result = payosGatewayService.handleWebhook(body);

        assertThat(result).isEqualTo(PayosGatewayService.WebhookResult.ACK);
        ArgumentCaptor<PaymentCallbackRequest> captor = ArgumentCaptor.forClass(PaymentCallbackRequest.class);
        verify(paymentService).handleCallback(captor.capture());
        assertThat(captor.getValue().gatewayName()).isEqualTo("PAYOS");
        assertThat(captor.getValue().gatewayTransactionId()).isEqualTo("FT261121998877");
        assertThat(captor.getValue().status()).isEqualTo("SUCCESS");
    }

    @Test
    void payos_handleWebhook_rejectsInvalidSignatureBeforeDbLookup() {
        Map<String, Object> data = Map.of(
                "orderCode", 42L,
                "amount", 2150000L,
                "reference", "FT261121998877"
        );
        Map<String, Object> body = Map.of(
                "code", "00",
                "data", data,
                "signature", "invalid_hmac_signature"
        );

        PayosGatewayService.WebhookResult result = payosGatewayService.handleWebhook(body);

        assertThat(result).isEqualTo(PayosGatewayService.WebhookResult.INVALID_SIGNATURE);
        verify(paymentRepository, never()).findWithBookingById(any());
        verify(paymentService, never()).handleCallback(any());
    }

    private Payment samplePayment(Long paymentId, String bookingCode, BigDecimal amount) {
        Booking booking = new Booking();
        booking.setId(100L);
        booking.setBookingCode(bookingCode);
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalAmount(amount);

        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setBooking(booking);
        payment.setAmount(amount);
        payment.setPaymentMethod("VNPAY");
        payment.setStatus(PaymentStatus.PENDING);
        return payment;
    }
}

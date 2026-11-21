package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.request.payment.PaymentCallbackRequest;
import com.example.domesticflightbookingwebbackend.dto.request.payment.PaymentRequest;
import com.example.domesticflightbookingwebbackend.dto.response.payment.PaymentResponse;
import com.example.domesticflightbookingwebbackend.entity.Booking;
import com.example.domesticflightbookingwebbackend.entity.BookingStatus;
import com.example.domesticflightbookingwebbackend.entity.Payment;
import com.example.domesticflightbookingwebbackend.entity.PaymentStatus;
import com.example.domesticflightbookingwebbackend.entity.User;
import com.example.domesticflightbookingwebbackend.entity.UserVoucher;
import com.example.domesticflightbookingwebbackend.entity.UserVoucherStatus;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.repository.BookingRepository;
import com.example.domesticflightbookingwebbackend.repository.PaymentRepository;
import com.example.domesticflightbookingwebbackend.repository.UserVoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private PaymentRepository paymentRepository;
    private BookingRepository bookingRepository;
    private UserVoucherRepository userVoucherRepository;
    private SeatHoldService seatHoldService;
    private ETicketService eTicketService;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        bookingRepository = mock(BookingRepository.class);
        userVoucherRepository = mock(UserVoucherRepository.class);
        seatHoldService = mock(SeatHoldService.class);
        eTicketService = mock(ETicketService.class);

        service = new PaymentService(
                paymentRepository,
                bookingRepository,
                userVoucherRepository,
                seatHoldService,
                eTicketService);
    }

    @Test
    void create_vnPayPending_traPaymentUrlVaRetryCount() {
        Booking booking = pendingBooking(1049L, 1L);
        when(bookingRepository.findWithUserById(1049L)).thenReturn(Optional.of(booking));
        when(paymentRepository.countByBookingId(1049L)).thenReturn(1L);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(500L);
            return p;
        });

        PaymentResponse res = service.create(1L, 1049L, new PaymentRequest("VNPAY", null, null));

        assertThat(res.id()).isEqualTo(500L);
        assertThat(res.paymentMethod()).isEqualTo("VNPAY");
        assertThat(res.status()).isEqualTo("PENDING");
        assertThat(res.retryCount()).isEqualTo(1);
        assertThat(res.paymentUrl()).contains("vnpayment.vn");
    }

    @Test
    void create_khongPhaiChuSoHuuBooking_nem1003ChongIdor() {
        Booking booking = pendingBooking(1049L, 2L);
        when(bookingRepository.findWithUserById(1049L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.create(1L, 1049L, new PaymentRequest("VNPAY", null, null)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void handleCallback_success_xacNhanBookingGheBookedVoucherUsedVaXuatETicket() {
        Booking booking = pendingBooking(1049L, 1L);
        Payment payment = new Payment();
        payment.setId(500L);
        payment.setBooking(booking);
        payment.setAmount(new BigDecimal("2959000.00"));
        payment.setPaymentMethod("VNPAY");
        payment.setGatewayName("VNPAY");
        payment.setStatus(PaymentStatus.PENDING);

        UserVoucher uv = new UserVoucher();
        uv.setId(88L);
        uv.setStatus(UserVoucherStatus.RESERVED);

        when(paymentRepository.findByGatewayNameIgnoreCaseAndGatewayTransactionId("VNPAY", "VNP-88213456"))
                .thenReturn(Optional.empty());
        when(paymentRepository.findWithBookingById(500L)).thenReturn(Optional.of(payment));
        when(userVoucherRepository.findByBookingId(1049L)).thenReturn(Optional.of(uv));

        PaymentResponse res = service.handleCallback(new PaymentCallbackRequest(
                500L, 1049L, "X7K2MP", "VNPAY", "VNP-88213456",
                "SUCCESS", new BigDecimal("2959000.00"), "SIG", null));

        assertThat(res.status()).isEqualTo("SUCCESS");
        assertThat(res.bookingStatus()).isEqualTo("CONFIRMED");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(uv.getStatus()).isEqualTo(UserVoucherStatus.USED);
        assertThat(uv.getUsedAt()).isNotNull();
        verify(seatHoldService, times(1)).confirmAll(1049L);
        verify(eTicketService, times(1)).issueForBooking(booking);
    }

    @Test
    void handleCallback_goiLapLaiCungGatewayTransactionId_idempotentKhongXuatVeTrung() {
        Booking booking = pendingBooking(1049L, 1L);
        booking.setStatus(BookingStatus.CONFIRMED);

        Payment alreadySuccess = new Payment();
        alreadySuccess.setId(500L);
        alreadySuccess.setBooking(booking);
        alreadySuccess.setAmount(new BigDecimal("2959000.00"));
        alreadySuccess.setPaymentMethod("VNPAY");
        alreadySuccess.setGatewayName("VNPAY");
        alreadySuccess.setGatewayTransactionId("VNP-88213456");
        alreadySuccess.setStatus(PaymentStatus.SUCCESS);
        alreadySuccess.setPaidAt(LocalDateTime.now().minusMinutes(1));

        when(paymentRepository.findByGatewayNameIgnoreCaseAndGatewayTransactionId("VNPAY", "VNP-88213456"))
                .thenReturn(Optional.of(alreadySuccess));

        PaymentResponse res = service.handleCallback(new PaymentCallbackRequest(
                500L, 1049L, "X7K2MP", "VNPAY", "VNP-88213456",
                "SUCCESS", new BigDecimal("2959000.00"), "SIG", null));

        assertThat(res.status()).isEqualTo("SUCCESS");
        assertThat(res.gatewayTransactionId()).isEqualTo("VNP-88213456");
        // Tuyệt đối không gọi lại confirmAll hay issueForBooking
        verify(seatHoldService, never()).confirmAll(any());
        verify(eTicketService, never()).issueForBooking(any());
    }

    @Test
    void handleCallback_failed_giuBookingPendingDeKhachRetry() {
        Booking booking = pendingBooking(1049L, 1L);
        Payment payment = new Payment();
        payment.setId(501L);
        payment.setBooking(booking);
        payment.setAmount(new BigDecimal("2959000.00"));
        payment.setPaymentMethod("VNPAY");
        payment.setGatewayName("VNPAY");
        payment.setStatus(PaymentStatus.PENDING);

        when(paymentRepository.findByGatewayNameIgnoreCaseAndGatewayTransactionId("VNPAY", "VNP-FAIL-01"))
                .thenReturn(Optional.empty());
        when(paymentRepository.findWithBookingById(501L)).thenReturn(Optional.of(payment));

        PaymentResponse res = service.handleCallback(new PaymentCallbackRequest(
                501L, 1049L, "X7K2MP", "VNPAY", "VNP-FAIL-01",
                "FAILED", new BigDecimal("2959000.00"), "SIG", "Không đủ số dư"));

        assertThat(res.status()).isEqualTo("FAILED");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        verify(eTicketService, never()).issueForBooking(any());
    }

    private Booking pendingBooking(Long id, Long userId) {
        User u = new User();
        u.setId(userId);

        Booking b = new Booking();
        b.setId(id);
        b.setBookingCode("X7K2MP");
        b.setUser(u);
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        b.setTotalAmount(new BigDecimal("2959000.00"));
        b.setCurrency("VND");
        b.setPaymentExpiresAt(LocalDateTime.now().plusMinutes(15));
        return b;
    }
}

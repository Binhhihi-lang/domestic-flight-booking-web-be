package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.request.payment.PaymentCallbackRequest;
import com.example.domesticflightbookingwebbackend.dto.request.payment.PaymentRequest;
import com.example.domesticflightbookingwebbackend.dto.response.payment.PaymentResponse;
import com.example.domesticflightbookingwebbackend.entity.Booking;
import com.example.domesticflightbookingwebbackend.entity.BookingStatus;
import com.example.domesticflightbookingwebbackend.entity.NotificationType;
import com.example.domesticflightbookingwebbackend.entity.Payment;
import com.example.domesticflightbookingwebbackend.entity.PaymentStatus;
import com.example.domesticflightbookingwebbackend.entity.UserVoucherStatus;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.repository.BookingRepository;
import com.example.domesticflightbookingwebbackend.repository.PaymentRepository;
import com.example.domesticflightbookingwebbackend.repository.UserVoucherRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Dịch vụ Thanh toán Mock Gateway & Xử lý Callback Idempotent (Phase 3B — B4.1).
 *
 * <p>Quy tắc nghiệp vụ (DB v0.5 mục 3.4 D.7, API v0.4 mục 3, Nghiệp vụ 09 mục 5.5):
 * <ul>
 *   <li>Chỉ hỗ trợ 2 phương thức thanh toán: {@code VNPAY} (chuyển hướng cổng thanh toán)
 *       và {@code BANK_TRANSFER} / {@code VIETQR} (chuyển khoản ngân hàng kèm mã QR).</li>
 *   <li>Chỉ thanh toán được cho đơn {@code PENDING_PAYMENT} chưa quá hạn {@code payment_expires_at}
 *       và có {@code total_amount > 0}.</li>
 *   <li>Hỗ trợ thử lại (retry): mỗi lần gọi {@code POST /api/bookings/{id}/payments} tạo một giao dịch
 *       {@link Payment} mới với {@code retry_count} bằng số lần đã tạo trước đó.</li>
 *   <li><b>Callback Idempotency theo {@code (gateway_name, gateway_transaction_id)}:</b>
 *       nếu cổng thanh toán gửi callback lặp lại cho một {@code gateway_transaction_id} đã ở trạng thái
 *       {@code SUCCESS}, hàm {@link #handleCallback(PaymentCallbackRequest)} trả về ngay lập tức mà không
 *       xác nhận lại booking hay phát hành vé điện tử trùng lặp.</li>
 *   <li>Khi thanh toán {@code SUCCESS}: chuyển {@code booking.status = CONFIRMED}, xác nhận toàn bộ ghế
 *       đang giữ ({@code seat_holds → CONFIRMED}, {@code flight_seats → BOOKED}), đổi voucher
 *       {@code RESERVED → USED} (ghi {@code used_at}), và phát hành vé điện tử ({@code e_tickets}).</li>
 * </ul>
 */
@Slf4j
@Service
public class PaymentService {

    private static final String VIETQR_BANK_ID = "VCB";
    private static final String VIETQR_BANK_NAME = "Vietcombank — CN TP. Hồ Chí Minh";
    private static final String VIETQR_ACCOUNT_NO = "1029384756";
    private static final String VIETQR_ACCOUNT_HOLDER = "CONG TY CO PHAN HANG KHONG VINAFLY";

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final UserVoucherRepository userVoucherRepository;
    private final SeatHoldService seatHoldService;
    private final ETicketService eTicketService;
    private NotificationService notificationService;
    private VnpayGatewayService vnpayGatewayService;
    private PayosGatewayService payosGatewayService;

    public PaymentService(
            PaymentRepository paymentRepository,
            BookingRepository bookingRepository,
            UserVoucherRepository userVoucherRepository,
            SeatHoldService seatHoldService,
            ETicketService eTicketService) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.userVoucherRepository = userVoucherRepository;
        this.seatHoldService = seatHoldService;
        this.eTicketService = eTicketService;
    }

    @Autowired(required = false)
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    public void setVnpayGatewayService(VnpayGatewayService vnpayGatewayService) {
        this.vnpayGatewayService = vnpayGatewayService;
    }

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    public void setPayosGatewayService(PayosGatewayService payosGatewayService) {
        this.payosGatewayService = payosGatewayService;
    }

    /**
     * Tạo giao dịch thanh toán mới cho đơn đặt vé ({@code POST /api/bookings/{bookingId}/payments}).
     */
    @Transactional
    public PaymentResponse create(Long userId, Long bookingId, PaymentRequest request) {
        Booking booking = bookingRepository.findWithUserById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn đặt vé không tồn tại"));
        if (!booking.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền thanh toán đơn đặt vé này");
        }
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new BusinessException("Đơn đặt vé này đã được thanh toán thành công trước đó");
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException("Không thể thanh toán đơn đặt vé ở trạng thái " + booking.getStatus());
        }
        if (booking.getPaymentExpiresAt() != null && booking.getPaymentExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Đơn đặt vé đã hết thời hạn thanh toán 15 phút");
        }

        BigDecimal amount = booking.getTotalAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Số tiền thanh toán phải lớn hơn 0");
        }

        String method = normalizePaymentMethod(request.paymentMethod());
        String gatewayName = resolveGatewayName(method, request.gatewayName());
        int retryCount = (int) paymentRepository.countByBookingId(bookingId);

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(amount);
        payment.setCurrency(booking.getCurrency() == null ? "VND" : booking.getCurrency());
        payment.setPaymentMethod(method);
        payment.setGatewayName(gatewayName);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setRetryCount(retryCount);

        Payment saved = paymentRepository.save(payment);

        // Nếu client gửi simulateResult = SUCCESS hoặc FAILED (Mock Gateway), xử lý ngay trong cùng luồng
        String simulate = request.simulateResult();
        if (simulate != null && !simulate.isBlank() && !"PENDING".equalsIgnoreCase(simulate)) {
            String mockTxnId = gatewayName + "-" + saved.getId() + "-"
                    + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            handleCallback(new PaymentCallbackRequest(
                    saved.getId(),
                    booking.getId(),
                    booking.getBookingCode(),
                    gatewayName,
                    mockTxnId,
                    simulate.trim().toUpperCase(),
                    amount,
                    "MOCK_SIG_OK",
                    "FAILED".equalsIgnoreCase(simulate) ? "Khách hàng hủy hoặc giả lập lỗi cổng thanh toán" : null));
            saved = paymentRepository.findWithBookingById(saved.getId()).orElse(saved);
        }

        log.info("Đã tạo giao dịch thanh toán id={} (method={}, status={}, retry={}) cho booking {}",
                saved.getId(), saved.getPaymentMethod(), saved.getStatus(), saved.getRetryCount(), booking.getBookingCode());
        return toResponse(saved);
    }

    /**
     * Xem chi tiết một giao dịch thanh toán của chính mình ({@code GET /api/payments/{paymentId}}).
     */
    @Transactional(readOnly = true)
    public PaymentResponse getSelf(Long userId, boolean isStaffOrAdmin, Long paymentId) {
        Payment payment = paymentRepository.findWithBookingById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Giao dịch thanh toán không tồn tại"));
        if (!isStaffOrAdmin && !payment.getBooking().getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền xem giao dịch thanh toán này");
        }
        return toResponse(payment);
    }

    /**
     * Nhận callback từ cổng thanh toán ({@code POST /api/payments/callback}).
     *
     * <p>Chống trùng (Idempotency) theo {@code (gateway_name, gateway_transaction_id)}:
     * nếu đã có bản ghi {@link Payment} mang mã giao dịch cổng này ở trạng thái {@code SUCCESS},
     * bỏ qua an toàn và không thực hiện lại {@link #confirmBooking(Booking)}.
     */
    @Transactional
    public PaymentResponse handleCallback(PaymentCallbackRequest request) {
        String gatewayName = request.gatewayName().trim().toUpperCase();
        String gatewayTxnId = request.gatewayTransactionId().trim();
        PaymentStatus callbackStatus = PaymentStatus.valueOf(request.status().trim().toUpperCase());

        // 1. Kiểm tra Idempotency: giao dịch với (gateway_name, gateway_transaction_id) đã từng xử lý chưa?
        Optional<Payment> existingByTxn = paymentRepository
                .findByGatewayNameIgnoreCaseAndGatewayTransactionId(gatewayName, gatewayTxnId);
        if (existingByTxn.isPresent()) {
            Payment prev = existingByTxn.get();
            if (prev.getStatus() == PaymentStatus.SUCCESS) {
                log.info("Callback lặp lại cho giao dịch đã SUCCESS (gateway={}, txnId={}) -> bỏ qua idempotent",
                        gatewayName, gatewayTxnId);
                return toResponse(prev);
            }
        }

        // 2. Xác định bản ghi Payment cần cập nhật
        Payment payment = existingByTxn.orElseGet(() -> resolveTargetPayment(request, gatewayName));
        Booking booking = payment.getBooking();

        // Nếu booking đã CONFIRMED bởi một lần thanh toán khác trước đó, giữ idempotent
        if (booking.getStatus() == BookingStatus.CONFIRMED && callbackStatus == PaymentStatus.SUCCESS) {
            if (payment.getStatus() != PaymentStatus.SUCCESS) {
                payment.setGatewayName(gatewayName);
                payment.setGatewayTransactionId(gatewayTxnId);
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setPaidAt(LocalDateTime.now());
                paymentRepository.save(payment);
            }
            return toResponse(payment);
        }

        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException("Đơn đặt vé " + booking.getBookingCode()
                    + " đang ở trạng thái " + booking.getStatus() + " — không thể xử lý callback thanh toán");
        }

        if (request.amount() != null && request.amount().compareTo(payment.getAmount()) != 0) {
            throw new BusinessException("Số tiền callback (" + request.amount()
                    + ") không khớp với số tiền giao dịch (" + payment.getAmount() + ")");
        }

        payment.setGatewayName(gatewayName);
        payment.setGatewayTransactionId(gatewayTxnId);

        if (callbackStatus == PaymentStatus.SUCCESS) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            paymentRepository.save(payment);
            confirmBooking(booking);
            log.info("Thanh toán THÀNH CÔNG: paymentId={}, txnId={}, booking={}",
                    payment.getId(), gatewayTxnId, booking.getBookingCode());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            failBooking(booking, request.failureReason());
            log.info("Thanh toán THẤT BẠI: paymentId={}, txnId={}, booking={}",
                    payment.getId(), gatewayTxnId, booking.getBookingCode());
        }

        return toResponse(payment);
    }

    // ================= Helpers =================

    private void confirmBooking(Booking booking) {
        LocalDateTime now = LocalDateTime.now();
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setUpdatedAt(now);
        bookingRepository.save(booking);

        // 1. Xác nhận ghế giữ -> BOOKED
        seatHoldService.confirmAll(booking.getId());

        // 2. Đánh dấu voucher đang RESERVED -> USED
        userVoucherRepository.findByBookingId(booking.getId()).ifPresent(uv -> {
            if (uv.getStatus() == UserVoucherStatus.RESERVED) {
                uv.setStatus(UserVoucherStatus.USED);
                uv.setUsedAt(now);
                userVoucherRepository.save(uv);
            }
        });

        // 3. Phát hành vé điện tử (E-Ticket) + gửi email xác nhận
        eTicketService.issueForBooking(booking);

        // 4. Phát thông báo BOOKING_CONFIRMED cho khách hàng (kèm dedupe_key chống trùng)
        if (notificationService != null && booking.getUser() != null) {
            notificationService.notify(
                    booking.getUser().getId(),
                    NotificationType.BOOKING_CONFIRMED,
                    "Đơn đặt vé " + booking.getBookingCode() + " đã thanh toán thành công",
                    "Cảm ơn quý khách đã thanh toán đơn đặt vé " + booking.getBookingCode() + ". Mã đặt chỗ (PNR) đã được kích hoạt.",
                    "/account/bookings/" + booking.getId(),
                    "booking:" + booking.getId() + ":confirmed");
        }
    }

    private void failBooking(Booking booking, String reason) {
        // Khi thanh toán thất bại, đơn vẫn giữ ở PENDING_PAYMENT (nếu còn trong hạn 15 phút)
        // để khách có thể bấm thanh toán lại (retry) như quy định tại Nghiệp vụ 09 mục 5.5.
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);
        log.debug("Giao dịch thanh toán của booking {} thất bại (lý do: {}), đơn vẫn cho phép thử lại trong hạn giữ chỗ",
                booking.getBookingCode(), reason);
    }

    private Payment resolveTargetPayment(PaymentCallbackRequest request, String gatewayName) {
        if (request.paymentId() != null) {
            return paymentRepository.findWithBookingById(request.paymentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Giao dịch thanh toán không tồn tại: " + request.paymentId()));
        }
        if (request.bookingId() != null) {
            return paymentRepository.findByBookingIdOrderByCreatedAtDesc(request.bookingId())
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy giao dịch thanh toán nào của đơn đặt vé id=" + request.bookingId()));
        }
        throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Callback phải cung cấp payment_id hoặc booking_id hợp lệ");
    }

    private String normalizePaymentMethod(String rawMethod) {
        String upper = rawMethod.trim().toUpperCase();
        if ("VIETQR".equals(upper) || "PAYOS".equals(upper)) {
            return "BANK_TRANSFER";
        }
        return upper;
    }

    private String resolveGatewayName(String method, String requestedGateway) {
        if (requestedGateway != null && !requestedGateway.isBlank()) {
            return requestedGateway.trim().toUpperCase();
        }
        if ("VNPAY".equals(method)) {
            return "VNPAY";
        }
        return (payosGatewayService != null && payosGatewayService.isConfigured()) ? "PAYOS" : "VIETQR";
    }

    private PaymentResponse toResponse(Payment payment) {
        Booking booking = payment.getBooking();
        String paymentUrl = null;
        PaymentResponse.VietQrInfo vietQr = null;

        if ("VNPAY".equalsIgnoreCase(payment.getPaymentMethod())) {
            if (vnpayGatewayService != null && vnpayGatewayService.isConfigured()) {
                paymentUrl = vnpayGatewayService.buildPaymentUrl(payment, "127.0.0.1");
            } else {
                paymentUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef="
                        + booking.getBookingCode() + "-" + payment.getId()
                        + "&vnp_Amount=" + payment.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
            }
        } else {
            if (payosGatewayService != null
                    && payosGatewayService.isConfigured()
                    && payment.getStatus() == PaymentStatus.PENDING) {
                vietQr = payosGatewayService.createPaymentLink(payment);
                if (vietQr != null && vietQr.checkoutUrl() != null) {
                    paymentUrl = vietQr.checkoutUrl();
                }
            }
            if (vietQr == null) {
                String transferContent = "VINAFLY " + booking.getBookingCode();
                String qrUrl = "https://img.vietqr.io/image/" + VIETQR_BANK_ID + "-" + VIETQR_ACCOUNT_NO
                        + "-compact2.png?amount=" + payment.getAmount().longValue()
                        + "&addInfo=" + transferContent.replace(" ", "%20");
                vietQr = new PaymentResponse.VietQrInfo(
                        VIETQR_BANK_NAME,
                        VIETQR_ACCOUNT_NO,
                        VIETQR_ACCOUNT_HOLDER,
                        payment.getAmount(),
                        transferContent,
                        qrUrl);
            }
        }

        return new PaymentResponse(
                payment.getId(),
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus().name(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentMethod(),
                payment.getGatewayName(),
                payment.getGatewayTransactionId(),
                payment.getStatus().name(),
                payment.getRetryCount(),
                paymentUrl,
                vietQr,
                payment.getCreatedAt(),
                payment.getPaidAt()
        );
    }
}

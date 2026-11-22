package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.dto.request.booking.ApplyVoucherRequest;
import com.example.domesticflightbookingwebbackend.dto.request.booking.BaggageListRequest;
import com.example.domesticflightbookingwebbackend.dto.request.booking.BookingRequest;
import com.example.domesticflightbookingwebbackend.dto.request.booking.CancelBookingRequest;
import com.example.domesticflightbookingwebbackend.dto.request.booking.PassengerListRequest;
import com.example.domesticflightbookingwebbackend.dto.response.booking.BookingDetailResponse;
import com.example.domesticflightbookingwebbackend.dto.response.booking.BookingSummaryResponse;
import com.example.domesticflightbookingwebbackend.dto.response.booking.HoldSeatResponse;
import com.example.domesticflightbookingwebbackend.entity.Airport;
import com.example.domesticflightbookingwebbackend.entity.BaggageOption;
import com.example.domesticflightbookingwebbackend.entity.Booking;
import com.example.domesticflightbookingwebbackend.entity.BookingBaggage;
import com.example.domesticflightbookingwebbackend.entity.BookingSegment;
import com.example.domesticflightbookingwebbackend.entity.BookingStatus;
import com.example.domesticflightbookingwebbackend.entity.City;
import com.example.domesticflightbookingwebbackend.entity.ETicket;
import com.example.domesticflightbookingwebbackend.entity.ETicketStatus;
import com.example.domesticflightbookingwebbackend.entity.FareClass;
import com.example.domesticflightbookingwebbackend.entity.FareRule;
import com.example.domesticflightbookingwebbackend.entity.Flight;
import com.example.domesticflightbookingwebbackend.entity.FlightOperationStatus;
import com.example.domesticflightbookingwebbackend.entity.NotificationType;
import com.example.domesticflightbookingwebbackend.entity.Passenger;
import com.example.domesticflightbookingwebbackend.entity.PassengerGender;
import com.example.domesticflightbookingwebbackend.entity.PassengerType;
import com.example.domesticflightbookingwebbackend.entity.Promotion;
import com.example.domesticflightbookingwebbackend.entity.SalesStatus;
import com.example.domesticflightbookingwebbackend.entity.SeatHold;
import com.example.domesticflightbookingwebbackend.entity.SeatHoldStatus;
import com.example.domesticflightbookingwebbackend.entity.TripType;
import com.example.domesticflightbookingwebbackend.entity.User;
import com.example.domesticflightbookingwebbackend.entity.UserVoucher;
import com.example.domesticflightbookingwebbackend.entity.UserVoucherStatus;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.repository.BaggageOptionRepository;
import com.example.domesticflightbookingwebbackend.repository.BookingBaggageRepository;
import com.example.domesticflightbookingwebbackend.repository.BookingRepository;
import com.example.domesticflightbookingwebbackend.repository.BookingSegmentRepository;
import com.example.domesticflightbookingwebbackend.repository.ETicketRepository;
import com.example.domesticflightbookingwebbackend.repository.FareClassRepository;
import com.example.domesticflightbookingwebbackend.repository.FareRuleRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightRepository;
import com.example.domesticflightbookingwebbackend.repository.PassengerRepository;
import com.example.domesticflightbookingwebbackend.repository.SeatHoldRepository;
import com.example.domesticflightbookingwebbackend.repository.UserRepository;
import com.example.domesticflightbookingwebbackend.repository.UserVoucherRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Nghiệp vụ Đặt chỗ — Khách hàng (Self-service) & Quản trị (ADMIN/STAFF) (Phase 3 — B3.3).
 *
 * <p>Quy tắc nghiệp vụ cốt lõi (DB v0.5 mục 3.4 & mục 5.3–5.5, API v0.4 mục 3):
 * <ul>
 *   <li>Tạo đơn ở trạng thái {@code PENDING_PAYMENT}, hạn thanh toán {@code payment_expires_at = now() + 15 phút}.</li>
 *   <li>Mọi thao tác self-service bắt buộc kiểm tra {@code booking.user.id == currentUserId} (chống IDOR).</li>
 *   <li>Số hành khách từ 1 đến 9, bắt buộc có ít nhất 1 người lớn ({@code ADULT}), số em bé ({@code INFANT}) ≤ số người lớn.</li>
 *   <li>Khi đặt vé chụp lại {@code fare_rule_id} hiện hành vào {@code booking_segments}.</li>
 *   <li>Áp voucher chỉ chọn từ ví ({@code user_voucher_id}), đổi trạng thái {@code AVAILABLE → RESERVED} và gắn {@code booking_id}.</li>
 *   <li><b>Hủy vé (Cancel Booking):</b> Chỉ cho phép hủy khi đơn ở {@code PENDING_PAYMENT} hoặc {@code CONFIRMED}
 *       và còn trước giờ khởi hành của chặng sớm nhất. Đổi {@code status = CANCELLED}, giải phóng toàn bộ ghế giữ,
 *       đổi {@code e_tickets} sang {@code CANCELLED}, nếu đơn chưa thanh toán thì trả voucher {@code RESERVED} về {@code AVAILABLE}.
 *       <b>Tuyệt đối KHÔNG tự động tính tiền hoàn và KHÔNG cộng ví</b> — trả kèm chính sách hoàn từ {@code fare_rules}
 *       và thông tin liên hệ đại lý ({@code 1900 1234} / {@code hotro@vinafly.vn}) để khách liên hệ xử lý ngoài hệ thống.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final String[] SORT_WHITELIST = {"created_at", "total_amount", "booking_code", "status"};
    private static final String PNR_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int PNR_LENGTH = 6;
    private static final int PAYMENT_HOLD_MINUTES = 15;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final BookingDetailResponse.AgencyContactInfo AGENCY_CONTACT =
            new BookingDetailResponse.AgencyContactInfo(
                    "Đại lý Vé máy bay VinaFly",
                    "1900 1234",
                    "hotro@vinafly.vn",
                    "Hệ thống không tự động hoàn tiền. Quý khách vui lòng liên hệ đại lý qua số điện thoại hoặc email kèm mã đặt chỗ (PNR) để được hỗ trợ hoàn vé theo quy định của hạng vé.");

    private final BookingRepository bookingRepository;
    private final BookingSegmentRepository bookingSegmentRepository;
    private final PassengerRepository passengerRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final BookingBaggageRepository bookingBaggageRepository;
    private final BaggageOptionRepository baggageOptionRepository;
    private final ETicketRepository eTicketRepository;
    private final FlightRepository flightRepository;
    private final FareClassRepository fareClassRepository;
    private final FareRuleRepository fareRuleRepository;
    private final UserRepository userRepository;
    private final UserVoucherRepository userVoucherRepository;
    private final PricingService pricingService;
    private final SeatHoldService seatHoldService;
    private NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Tạo đơn đặt vé mới ở trạng thái {@code PENDING_PAYMENT} (giữ 15 phút).
     */
    @Transactional
    public BookingDetailResponse create(Long userId, BookingRequest request) {
        User user = userRepository.findById(userId)
                .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản người dùng không tồn tại"));

        TripType tripType = TripType.valueOf(request.tripType().trim().toUpperCase());
        validateSegmentsForTripType(tripType, request.segments());

        if (request.passengers() != null && !request.passengers().isEmpty()) {
            validatePassengerComposition(request.passengers());
        }

        LocalDateTime now = LocalDateTime.now();
        Booking booking = new Booking();
        booking.setBookingCode(generateBookingCode());
        booking.setOrderCode(generateOrderCode());
        booking.setUser(user);
        booking.setTripType(tripType);
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setCurrency("VND");
        booking.setPaymentExpiresAt(now.plusMinutes(PAYMENT_HOLD_MINUTES));
        booking.setFareAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        booking.setPromotionDiscountAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        booking.setVoucherDiscountAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        booking.setSeatSurchargeAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        booking.setBaggageAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        booking.setTotalAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        booking = bookingRepository.save(booking);

        // Lưu danh sách hành khách nếu truyền ngay lúc tạo đơn
        List<Passenger> savedPassengers = new ArrayList<>();
        if (request.passengers() != null && !request.passengers().isEmpty()) {
            savedPassengers = savePassengersInternal(booking, request.passengers());
        }

        // Lưu các chặng bay (booking_segments)
        List<BookingSegment> savedSegments = new ArrayList<>();
        int order = 1;
        for (BookingRequest.SegmentItem item : request.segments()) {
            Flight flight = resolveBookableFlight(item.flightId());
            FareClass fareClass = resolveBookableFareClass(flight, item.fareClassId());
            FareRule activeRule = resolveLatestFareRule(fareClass);

            PricingService.SegmentPricingResult segPrice = pricingService.priceSegmentWithBestPromotion(
                    flight, fareClass, tripType, savedPassengers);

            BookingSegment segment = new BookingSegment();
            segment.setBooking(booking);
            segment.setFlight(flight);
            segment.setSegmentOrder(item.segmentOrder() != null ? item.segmentOrder() : order);
            segment.setFareClass(fareClass);
            segment.setFareRule(activeRule);
            segment.setFareAmountAtBooking(segPrice.rawFareAmount());
            segment.setPromotion(segPrice.bestPromotion());
            segment.setPromotionDiscountAmount(segPrice.promotionDiscountAmount());
            segment.setCurrency("VND");
            savedSegments.add(bookingSegmentRepository.save(segment));
            order++;
        }

        recalculateTotals(booking, savedSegments, savedPassengers);

        // Nếu gửi kèm user_voucher_id ngay lúc tạo đơn thì áp luôn
        if (request.userVoucherId() != null) {
            applyVoucherInternal(userId, booking, savedSegments, savedPassengers, request.userVoucherId());
        }

        log.info("Đã tạo đơn đặt vé {} (id={}) cho userId={}, total={}",
                booking.getBookingCode(), booking.getId(), userId, booking.getTotalAmount());
        return buildDetailResponse(booking);
    }

    /** Danh sách đơn đặt vé của chính khách hàng đang đăng nhập ({@code GET /api/bookings}). */
    @Transactional(readOnly = true)
    public PageData<BookingSummaryResponse> listSelf(Long userId, ListQuery query, String status) {
        Pageable pageable = PageMapper.toPageable(query, "createdAt", SORT_WHITELIST);
        Specification<Booking> spec = buildSpec(query, status, userId);
        Page<Booking> page = bookingRepository.findAll(spec, pageable);
        return toSummaryPageData(page);
    }

    /** Chi tiết một đơn đặt vé của chính khách hàng đang đăng nhập (kiểm tra {@code user_id} chống IDOR). */
    @Transactional(readOnly = true)
    public BookingDetailResponse getSelf(Long userId, Long bookingId) {
        Booking booking = findOwnedBooking(userId, bookingId);
        return buildDetailResponse(booking);
    }

    /**
     * Hủy đơn đặt vé của khách hàng ({@code POST /api/bookings/{id}/cancel}).
     * Không tự động hoàn tiền, không cộng ví (DB v0.5 mục 5.5).
     */
    @Transactional
    public BookingDetailResponse cancel(Long userId, Long bookingId, CancelBookingRequest request) {
        Booking booking = findOwnedBooking(userId, bookingId);
        return cancelBookingInternal(booking, request == null ? null : request.cancelReason());
    }

    /** Hủy đơn đặt vé từ phía Quản trị (ADMIN/STAFF) khi vận hành cần hỗ trợ khách. */
    @Transactional
    public BookingDetailResponse cancelByAdmin(Long bookingId, CancelBookingRequest request) {
        Booking booking = findBookingOrThrow(bookingId);
        return cancelBookingInternal(booking, request == null ? null : request.cancelReason());
    }

    /**
     * Thêm hoặc thay thế danh sách hành khách của đơn đặt vé ({@code POST /api/bookings/{id}/passengers}).
     */
    @Transactional
    public BookingDetailResponse addPassengers(Long userId, Long bookingId, PassengerListRequest request) {
        Booking booking = findOwnedPendingBooking(userId, bookingId);
        validatePassengerComposition(request.passengers());

        // Nếu đơn đã có giữ ghế hoặc hành lý gắn với hành khách cũ thì giải phóng trước khi thay hành khách
        seatHoldService.releaseAllForBooking(bookingId);
        bookingBaggageRepository.deleteByBookingId(bookingId);
        List<Passenger> oldPassengers = passengerRepository.findByBookingIdOrderByIdAsc(bookingId);
        if (!oldPassengers.isEmpty()) {
            passengerRepository.deleteAll(oldPassengers);
            passengerRepository.flush();
        }

        booking.setSeatSurchargeAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        booking.setBaggageAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        List<Passenger> savedPassengers = savePassengersInternal(booking, request.passengers());
        List<BookingSegment> segments = bookingSegmentRepository.findByBookingIdOrderBySegmentOrderAsc(bookingId);
        recalculateTotals(booking, segments, savedPassengers);

        log.info("Cập nhật {} hành khách cho booking {}", savedPassengers.size(), booking.getBookingCode());
        return buildDetailResponse(booking);
    }

    /**
     * Áp hoặc gỡ voucher từ ví khách vào đơn đặt vé ({@code POST /api/bookings/{id}/voucher}).
     */
    @Transactional
    public BookingDetailResponse applyVoucher(Long userId, Long bookingId, ApplyVoucherRequest request) {
        Booking booking = findOwnedPendingBooking(userId, bookingId);
        List<BookingSegment> segments = bookingSegmentRepository.findByBookingIdOrderBySegmentOrderAsc(bookingId);
        List<Passenger> passengers = passengerRepository.findByBookingIdOrderByIdAsc(bookingId);

        Long userVoucherId = request == null ? null : request.userVoucherId();
        applyVoucherInternal(userId, booking, segments, passengers, userVoucherId);

        return buildDetailResponse(booking);
    }

    /**
     * Chọn gói hành lý ký gửi cho các hành khách trong đơn ({@code POST /api/bookings/{id}/baggages}).
     */
    @Transactional
    public BookingDetailResponse addBaggages(Long userId, Long bookingId, BaggageListRequest request) {
        Booking booking = findOwnedPendingBooking(userId, bookingId);
        bookingBaggageRepository.deleteByBookingId(bookingId);
        bookingBaggageRepository.flush();

        BigDecimal totalBaggage = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (request.baggages() != null && !request.baggages().isEmpty()) {
            List<Passenger> passengers = passengerRepository.findByBookingIdOrderByIdAsc(bookingId);
            Map<Long, Passenger> passengerById = new HashMap<>();
            for (Passenger p : passengers) {
                passengerById.put(p.getId(), p);
            }

            for (BaggageListRequest.BaggageItem item : request.baggages()) {
                Passenger passenger = passengerById.get(item.passengerId());
                if (passenger == null) {
                    throw new ResourceNotFoundException("Hành khách không thuộc đơn đặt vé này: " + item.passengerId());
                }
                if (passenger.getPassengerType() == PassengerType.INFANT) {
                    throw new BusinessException("Hành khách em bé (INFANT) không được mua gói hành lý ký gửi riêng");
                }
                BaggageOption option = baggageOptionRepository.findById(item.baggageOptionId())
                        .filter(bo -> !Boolean.TRUE.equals(bo.getIsDeleted()))
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Gói hành lý không tồn tại: " + item.baggageOptionId()));
                if (!Boolean.TRUE.equals(option.getIsActive())) {
                    throw new BusinessException("Gói hành lý đang ngừng hoạt động: " + option.getName());
                }

                BigDecimal unitPrice = option.getPrice().setScale(2, RoundingMode.HALF_UP);
                BookingBaggage bb = new BookingBaggage();
                bb.setBooking(booking);
                bb.setPassenger(passenger);
                bb.setBaggageOption(option);
                bb.setQuantity(item.quantity());
                bb.setUnitPriceAtBooking(unitPrice);
                bb.setCurrency("VND");
                bookingBaggageRepository.save(bb);

                totalBaggage = totalBaggage.add(unitPrice.multiply(BigDecimal.valueOf(item.quantity())));
            }
        }

        booking.setBaggageAmount(totalBaggage.setScale(2, RoundingMode.HALF_UP));
        booking.setTotalAmount(pricingService.computeTotal(booking));
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        log.info("Cập nhật hành lý cho booking {}: baggageAmount={}, total={}",
                booking.getBookingCode(), booking.getBaggageAmount(), booking.getTotalAmount());
        return buildDetailResponse(booking);
    }

    /** Danh sách toàn bộ đơn đặt vé trên hệ thống cho ADMIN / STAFF ({@code GET /api/admin/bookings}). */
    @Transactional(readOnly = true)
    public PageData<BookingSummaryResponse> listAll(ListQuery query, String status, Long userId) {
        Pageable pageable = PageMapper.toPageable(query, "createdAt", SORT_WHITELIST);
        Specification<Booking> spec = buildSpec(query, status, userId);
        Page<Booking> page = bookingRepository.findAll(spec, pageable);
        return toSummaryPageData(page);
    }

    /** Chi tiết một đơn đặt vé cho ADMIN / STAFF ({@code GET /api/admin/bookings/{id}}). */
    @Transactional(readOnly = true)
    public BookingDetailResponse getById(Long bookingId) {
        Booking booking = findBookingOrThrow(bookingId);
        return buildDetailResponse(booking);
    }

    /**
     * Tra cứu công khai theo mã đơn hàng (DH...) hoặc mã đặt chỗ (PNR) và email chủ đơn
     * ({@code GET /api/public/bookings/lookup}). Không tra theo ID tuần tự để tránh dò đơn.
     */
    @Transactional(readOnly = true)
    public BookingDetailResponse lookupPublic(String bookingCode, String email) {
        if (bookingCode == null || bookingCode.isBlank() || email == null || email.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Vui lòng nhập đầy đủ mã đặt chỗ và email");
        }
        String cleanCode = bookingCode.trim().toUpperCase();
        String cleanEmail = email.trim().toLowerCase();

        Booking booking = bookingRepository.findWithUserByOrderCodeIgnoreCase(cleanCode).orElse(null);
        if (booking == null) {
            booking = bookingRepository.findWithUserByBookingCodeIgnoreCase(cleanCode).orElse(null);
        }
        if (booking == null || booking.getUser() == null
                || !cleanEmail.equalsIgnoreCase(booking.getUser().getEmail())) {
            throw new ResourceNotFoundException("Không tìm thấy đơn đặt chỗ khớp với mã và email đã nhập");
        }
        return buildDetailResponse(booking);
    }

    // ================= Helpers =================

    private Booking findBookingOrThrow(Long bookingId) {
        return bookingRepository.findWithUserById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn đặt vé không tồn tại"));
    }

    private Booking findOwnedBooking(Long userId, Long bookingId) {
        Booking booking = findBookingOrThrow(bookingId);
        if (!booking.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền truy cập đơn đặt vé này");
        }
        return booking;
    }

    private Booking findOwnedPendingBooking(Long userId, Long bookingId) {
        Booking booking = findOwnedBooking(userId, bookingId);
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException("Chỉ có thể chỉnh sửa đơn đặt vé khi đang ở trạng thái PENDING_PAYMENT");
        }
        if (booking.getPaymentExpiresAt() != null && booking.getPaymentExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Đơn đặt vé đã hết thời hạn thanh toán 15 phút");
        }
        return booking;
    }

    private BookingDetailResponse cancelBookingInternal(Booking booking, String cancelReason) {
        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.EXPIRED) {
            throw new BusinessException("Đơn đặt vé đang ở trạng thái " + booking.getStatus() + " — không thể hủy");
        }

        List<BookingSegment> segments = bookingSegmentRepository.findByBookingIdOrderBySegmentOrderAsc(booking.getId());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime earliestDeparture = segments.stream()
                .map(s -> s.getFlight().getScheduledDeparture())
                .min(Comparator.naturalOrder())
                .orElse(null);

        if (earliestDeparture != null && !now.isBefore(earliestDeparture)) {
            throw new BusinessException("Không thể hủy đơn đặt vé khi chuyến bay của chặng sớm nhất đã đến giờ khởi hành");
        }

        BookingStatus previousStatus = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(now);
        booking.setCancelReason(cancelReason == null || cancelReason.isBlank()
                ? "Khách hàng yêu cầu hủy vé"
                : cancelReason.trim());
        booking.setUpdatedAt(now);
        bookingRepository.save(booking);

        releaseResources(booking, previousStatus, now);
        if (notificationService != null && booking.getUser() != null) {
            notificationService.notify(
                    booking.getUser().getId(),
                    NotificationType.BOOKING_CANCELLED,
                    "Đơn đặt vé " + booking.getBookingCode() + " đã huỷ",
                    "Đơn đặt vé " + booking.getBookingCode() + " đã được huỷ (" + booking.getCancelReason() + ").",
                    "/account/bookings/" + booking.getId(),
                    "booking:" + booking.getId() + ":cancelled");
        }
        log.info("Đã hủy đơn đặt vé {} (trạng thái trước khi hủy: {}) — không tự động hoàn tiền",
                booking.getBookingCode(), previousStatus);
        return buildDetailResponse(booking);
    }

    /**
     * Giải phóng tài nguyên khi hủy vé (DB v0.5 mục 5.5):
     * 1. Giải phóng ghế giữ ({@code seat_holds → RELEASED}, {@code flight_seats → AVAILABLE}).
     * 2. Hủy vé điện tử ({@code e_tickets → CANCELLED}).
     * 3. Nếu đơn chưa thanh toán ({@code PENDING_PAYMENT}), trả voucher đang {@code RESERVED} về {@code AVAILABLE}.
     */
    private void releaseResources(Booking booking, BookingStatus previousStatus, LocalDateTime now) {
        // 1. Nhả ghế
        seatHoldService.releaseAllForBooking(booking.getId());

        // 2. Hủy e-tickets
        List<ETicket> tickets = eTicketRepository.findByBookingIdOrderByIdAsc(booking.getId());
        for (ETicket t : tickets) {
            t.setStatus(ETicketStatus.CANCELLED);
        }
        if (!tickets.isEmpty()) {
            eTicketRepository.saveAll(tickets);
        }

        // 3. Nếu đơn đang PENDING_PAYMENT (chưa thanh toán) thì trả voucher RESERVED về ví
        if (previousStatus == BookingStatus.PENDING_PAYMENT) {
            userVoucherRepository.findByBookingId(booking.getId()).ifPresent(uv -> {
                if (uv.getStatus() == UserVoucherStatus.RESERVED) {
                    if (uv.getExpiresAt() != null && uv.getExpiresAt().isBefore(now)) {
                        uv.setStatus(UserVoucherStatus.EXPIRED);
                    } else {
                        uv.setStatus(UserVoucherStatus.AVAILABLE);
                    }
                    uv.setBooking(null);
                    uv.setDiscountAmount(null);
                    userVoucherRepository.save(uv);
                }
            });
        }
    }

    private void applyVoucherInternal(
            Long userId,
            Booking booking,
            List<BookingSegment> segments,
            List<Passenger> passengers,
            Long userVoucherId) {
        // Gỡ voucher cũ (nếu đơn đang gắn một voucher RESERVED)
        userVoucherRepository.findByBookingId(booking.getId()).ifPresent(oldUv -> {
            if (oldUv.getStatus() == UserVoucherStatus.RESERVED) {
                oldUv.setStatus(UserVoucherStatus.AVAILABLE);
                oldUv.setBooking(null);
                oldUv.setDiscountAmount(null);
                userVoucherRepository.saveAndFlush(oldUv);
            }
        });

        if (userVoucherId == null) {
            booking.setVoucherDiscountAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            booking.setTotalAmount(pricingService.computeTotal(booking));
            booking.setUpdatedAt(LocalDateTime.now());
            bookingRepository.save(booking);
            return;
        }

        UserVoucher userVoucher = userVoucherRepository.findByIdAndUserId(userVoucherId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher không tồn tại trong ví của bạn"));

        BigDecimal voucherDiscount = pricingService.applyVoucher(booking, segments, passengers, userVoucher);

        userVoucher.setStatus(UserVoucherStatus.RESERVED);
        userVoucher.setBooking(booking);
        userVoucher.setDiscountAmount(voucherDiscount);
        userVoucherRepository.save(userVoucher);

        booking.setVoucherDiscountAmount(voucherDiscount);
        booking.setTotalAmount(pricingService.computeTotal(booking));
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);
    }

    private void recalculateTotals(
            Booking booking,
            List<BookingSegment> segments,
            List<Passenger> passengers) {
        BigDecimal totalFare = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPromo = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (BookingSegment seg : segments) {
            PricingService.SegmentPricingResult segPrice = pricingService.priceSegmentWithBestPromotion(
                    seg.getFlight(), seg.getFareClass(), booking.getTripType(), passengers);
            seg.setFareAmountAtBooking(segPrice.rawFareAmount());
            seg.setPromotion(segPrice.bestPromotion());
            seg.setPromotionDiscountAmount(segPrice.promotionDiscountAmount());
            bookingSegmentRepository.save(seg);

            totalFare = totalFare.add(segPrice.rawFareAmount());
            totalPromo = totalPromo.add(segPrice.promotionDiscountAmount());
        }

        booking.setFareAmount(totalFare);
        booking.setPromotionDiscountAmount(totalPromo);

        // Nếu đang có voucher RESERVED gắn vào đơn, tính lại mức giảm của voucher
        userVoucherRepository.findByBookingId(booking.getId()).ifPresent(uv -> {
            if (uv.getStatus() == UserVoucherStatus.RESERVED) {
                try {
                    BigDecimal newVoucherDisc = pricingService.applyVoucher(booking, segments, passengers, uv);
                    uv.setDiscountAmount(newVoucherDisc);
                    userVoucherRepository.save(uv);
                    booking.setVoucherDiscountAmount(newVoucherDisc);
                } catch (BusinessException ex) {
                    // Nếu sau khi đổi hành khách không còn đủ điều kiện voucher thì tự gỡ voucher về AVAILABLE
                    uv.setStatus(UserVoucherStatus.AVAILABLE);
                    uv.setBooking(null);
                    uv.setDiscountAmount(null);
                    userVoucherRepository.save(uv);
                    booking.setVoucherDiscountAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                }
            }
        });

        booking.setTotalAmount(pricingService.computeTotal(booking));
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);
    }

    private List<Passenger> savePassengersInternal(
            Booking booking,
            List<BookingRequest.PassengerItem> items) {
        List<Passenger> list = new ArrayList<>();
        for (BookingRequest.PassengerItem item : items) {
            Passenger p = new Passenger();
            p.setBooking(booking);
            p.setFullName(item.fullName().trim());
            p.setGender(PassengerGender.valueOf(item.gender().trim().toUpperCase()));
            p.setBirthDate(item.birthDate());
            p.setPassengerType(PassengerType.valueOf(item.passengerType().trim().toUpperCase()));
            p.setPassportNumber(item.passportNumber() == null ? null : item.passportNumber().trim());
            p.setPhone(item.phone() == null ? null : item.phone().trim());
            p.setEmail(item.email() == null ? null : item.email().trim());
            list.add(passengerRepository.save(p));
        }
        return list;
    }

    private void validateSegmentsForTripType(TripType tripType, List<BookingRequest.SegmentItem> segments) {
        if (tripType == TripType.ONE_WAY && segments.size() != 1) {
            throw new BusinessException("Hành trình một chiều (ONE_WAY) phải có đúng 1 chặng bay");
        }
        if (tripType == TripType.ROUND_TRIP && segments.size() != 2) {
            throw new BusinessException("Hành trình khứ hồi (ROUND_TRIP) phải có đúng 2 chặng bay");
        }
        Set<Long> flightIds = new HashSet<>();
        for (BookingRequest.SegmentItem s : segments) {
            if (!flightIds.add(s.flightId())) {
                throw new BusinessException("Hai chặng bay trong cùng một đơn đặt vé không được trùng chuyến bay");
            }
        }
    }

    private void validatePassengerComposition(List<BookingRequest.PassengerItem> passengers) {
        if (passengers.size() > 9) {
            throw new BusinessException("Một đơn đặt vé chỉ cho phép tối đa 9 hành khách");
        }
        int adults = 0;
        int infants = 0;
        for (BookingRequest.PassengerItem p : passengers) {
            PassengerType type = PassengerType.valueOf(p.passengerType().trim().toUpperCase());
            if (type == PassengerType.ADULT) {
                adults++;
            } else if (type == PassengerType.INFANT) {
                infants++;
            }
        }
        if (adults < 1) {
            throw new BusinessException("Đơn đặt vé bắt buộc phải có ít nhất 1 hành khách người lớn (ADULT)");
        }
        if (infants > adults) {
            throw new BusinessException("Số lượng em bé (INFANT) không được vượt quá số lượng người lớn (ADULT)");
        }
    }

    private Flight resolveBookableFlight(Long flightId) {
        Flight flight = flightRepository.findWithDetailsById(flightId)
                .filter(f -> !Boolean.TRUE.equals(f.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Chuyến bay không tồn tại: " + flightId));
        if (flight.getSalesStatus() != SalesStatus.OPEN) {
            throw new BusinessException("Chuyến bay " + flight.getFlightNumber() + " hiện đang đóng bán");
        }
        if (flight.getOperationStatus() == FlightOperationStatus.CANCELLED
                || flight.getOperationStatus() == FlightOperationStatus.DEPARTED
                || flight.getOperationStatus() == FlightOperationStatus.ARRIVED) {
            throw new BusinessException("Chuyến bay " + flight.getFlightNumber()
                    + " không thể đặt vé ở trạng thái " + flight.getOperationStatus());
        }
        return flight;
    }

    private FareClass resolveBookableFareClass(Flight flight, Long fareClassId) {
        FareClass fc = fareClassRepository.findById(fareClassId)
                .filter(f -> !Boolean.TRUE.equals(f.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Hạng vé không tồn tại: " + fareClassId));
        if (!Boolean.TRUE.equals(fc.getIsActive())) {
            throw new BusinessException("Hạng vé " + fc.getFareClassCode() + " hiện đã ngừng bán");
        }
        if (!fc.getAirline().getId().equals(flight.getAirline().getId())) {
            throw new BusinessException("Hạng vé " + fc.getFareClassCode()
                    + " không thuộc hãng khai thác chuyến bay " + flight.getFlightNumber());
        }
        return fc;
    }

    private FareRule resolveLatestFareRule(FareClass fareClass) {
        return fareRuleRepository.findActiveRulesByFareClassId(fareClass.getId(), LocalDateTime.now())
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "Hạng vé " + fareClass.getFareClassCode() + " chưa được cấu hình quy định hoàn vé"));
    }

    private String generateBookingCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(PNR_LENGTH);
            for (int i = 0; i < PNR_LENGTH; i++) {
                sb.append(PNR_CHARS.charAt(RANDOM.nextInt(PNR_CHARS.length())));
            }
            String code = sb.toString();
            if (!bookingRepository.existsByBookingCode(code)) {
                return code;
            }
        }
        return "VF" + (System.currentTimeMillis() % 1_000_000);
    }

    /**
     * Sinh mã đơn hàng công khai dạng {@code "DH" + System.currentTimeMillis()}
     * để chống dò ID tuần tự; tự động tịnh tiến +1ms nếu trùng cùng mili-giây.
     */
    private String generateOrderCode() {
        long base = System.currentTimeMillis();
        for (int attempt = 0; attempt < 50; attempt++) {
            String code = "DH" + (base + attempt);
            if (!bookingRepository.existsByOrderCode(code)) {
                return code;
            }
        }
        return "DH" + System.currentTimeMillis() + RANDOM.nextInt(90) + 10;
    }

    private PageData<BookingSummaryResponse> toSummaryPageData(Page<Booking> page) {
        List<Booking> bookings = page.getContent();
        if (bookings.isEmpty()) {
            return PageMapper.toPageData(page, b -> null);
        }

        List<Long> bookingIds = bookings.stream().map(Booking::getId).toList();
        Map<Long, List<BookingSegment>> segmentsByBooking = new HashMap<>();
        for (BookingSegment seg : bookingSegmentRepository.findByBookingIdInOrderBySegmentOrderAsc(bookingIds)) {
            segmentsByBooking.computeIfAbsent(seg.getBooking().getId(), k -> new ArrayList<>()).add(seg);
        }

        Map<Long, Integer> paxCountByBooking = new HashMap<>();
        for (Passenger p : passengerRepository.findByBookingIdInOrderByIdAsc(bookingIds)) {
            paxCountByBooking.merge(p.getBooking().getId(), 1, Integer::sum);
        }

        return PageMapper.toPageData(page, b -> {
            User u = b.getUser();
            List<BookingSummaryResponse.SegmentSummary> segSummaries = segmentsByBooking
                    .getOrDefault(b.getId(), List.of())
                    .stream()
                    .map(this::toSegmentSummary)
                    .toList();
            return new BookingSummaryResponse(
                    b.getId(),
                    b.getBookingCode(),
                    b.getOrderCode(),
                    u.getId(),
                    u.getFullName(),
                    u.getEmail(),
                    u.getPhone(),
                    b.getTripType().name(),
                    b.getStatus().name(),
                    paxCountByBooking.getOrDefault(b.getId(), 0),
                    b.getFareAmount(),
                    b.getPromotionDiscountAmount(),
                    b.getVoucherDiscountAmount(),
                    b.getSeatSurchargeAmount(),
                    b.getBaggageAmount(),
                    b.getTotalAmount(),
                    b.getCurrency(),
                    b.getPaymentExpiresAt(),
                    b.getCancelledAt(),
                    b.getCancelReason(),
                    b.getCreatedAt(),
                    segSummaries
            );
        });
    }

    private BookingSummaryResponse.SegmentSummary toSegmentSummary(BookingSegment seg) {
        Flight f = seg.getFlight();
        Airport dep = f.getDepartureAirport();
        Airport arr = f.getArrivalAirport();
        City depCity = dep.getCity();
        City arrCity = arr.getCity();
        FareClass fc = seg.getFareClass();
        return new BookingSummaryResponse.SegmentSummary(
                seg.getId(),
                seg.getSegmentOrder(),
                f.getId(),
                f.getFlightNumber(),
                f.getAirline().getAirlineCode(),
                f.getAirline().getAirlineName(),
                dep.getAirportCode(),
                depCity == null ? null : depCity.getCityName(),
                arr.getAirportCode(),
                arrCity == null ? null : arrCity.getCityName(),
                f.getScheduledDeparture(),
                f.getScheduledArrival(),
                fc.getFareClassCode(),
                fc.getFareClassName(),
                fc.getSeatClass().name()
        );
    }

    private BookingDetailResponse buildDetailResponse(Booking booking) {
        User u = booking.getUser();
        List<BookingSegment> segments = bookingSegmentRepository.findByBookingIdOrderBySegmentOrderAsc(booking.getId());
        List<Passenger> passengers = passengerRepository.findByBookingIdOrderByIdAsc(booking.getId());
        List<SeatHold> holds = seatHoldRepository.findByBookingIdOrderByIdAsc(booking.getId());
        List<BookingBaggage> baggages = bookingBaggageRepository.findByBookingIdOrderByIdAsc(booking.getId());

        BookingDetailResponse.AppliedVoucherInfo voucherInfo = userVoucherRepository.findByBookingId(booking.getId())
                .map(uv -> new BookingDetailResponse.AppliedVoucherInfo(
                        uv.getId(),
                        uv.getVoucher().getId(),
                        uv.getVoucher().getVoucherCode(),
                        uv.getVoucher().getVoucherName(),
                        uv.getDiscountAmount(),
                        uv.getStatus().name()))
                .orElse(null);

        List<BookingDetailResponse.SegmentDetail> segmentDetails = segments.stream()
                .map(seg -> {
                    Flight f = seg.getFlight();
                    Airport dep = f.getDepartureAirport();
                    Airport arr = f.getArrivalAirport();
                    FareClass fc = seg.getFareClass();
                    FareRule fr = seg.getFareRule();
                    Promotion promo = seg.getPromotion();
                    return new BookingDetailResponse.SegmentDetail(
                            seg.getId(),
                            seg.getSegmentOrder(),
                            f.getId(),
                            f.getFlightNumber(),
                            f.getAirline().getAirlineCode(),
                            f.getAirline().getAirlineName(),
                            f.getAircraft().getAircraftCode(),
                            f.getAircraft().getModel(),
                            dep.getAirportCode(),
                            dep.getAirportName(),
                            dep.getCity() == null ? null : dep.getCity().getCityName(),
                            arr.getAirportCode(),
                            arr.getAirportName(),
                            arr.getCity() == null ? null : arr.getCity().getCityName(),
                            f.getScheduledDeparture(),
                            f.getScheduledArrival(),
                            fc.getId(),
                            fc.getFareClassCode(),
                            fc.getFareClassName(),
                            fc.getSeatClass().name(),
                            seg.getFareAmountAtBooking(),
                            promo == null ? null : promo.getId(),
                            promo == null ? null : promo.getPromotionCode(),
                            promo == null ? null : promo.getPromotionName(),
                            seg.getPromotionDiscountAmount(),
                            seg.getCurrency(),
                            new BookingDetailResponse.FareRuleSnapshot(
                                    fr.getId(),
                                    fr.getVersion(),
                                    fr.getIsRefundable(),
                                    fr.getRefundMinHoursBeforeDeparture(),
                                    fr.getRefundPercent(),
                                    fr.getDescription())
                    );
                })
                .toList();

        List<BookingDetailResponse.PassengerDetail> passengerDetails = passengers.stream()
                .map(p -> new BookingDetailResponse.PassengerDetail(
                        p.getId(),
                        p.getFullName(),
                        p.getGender().name(),
                        p.getBirthDate(),
                        p.getPassengerType().name(),
                        p.getPassportNumber(),
                        p.getPhone(),
                        p.getEmail()))
                .toList();

        List<HoldSeatResponse> seatHoldResponses = holds.stream()
                .filter(h -> h.getStatus() != SeatHoldStatus.RELEASED)
                .map(seatHoldService::toResponse)
                .toList();

        List<BookingDetailResponse.BaggageDetail> baggageDetails = baggages.stream()
                .map(bb -> new BookingDetailResponse.BaggageDetail(
                        bb.getId(),
                        bb.getPassenger().getId(),
                        bb.getPassenger().getFullName(),
                        bb.getBaggageOption().getId(),
                        bb.getBaggageOption().getName(),
                        bb.getBaggageOption().getWeightKg(),
                        bb.getQuantity(),
                        bb.getUnitPriceAtBooking(),
                        bb.getUnitPriceAtBooking()
                                .multiply(BigDecimal.valueOf(bb.getQuantity()))
                                .setScale(2, RoundingMode.HALF_UP),
                        bb.getCurrency()))
                .toList();

        return new BookingDetailResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getOrderCode(),
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                u.getPhone(),
                booking.getTripType().name(),
                booking.getStatus().name(),
                booking.getFareAmount(),
                booking.getPromotionDiscountAmount(),
                booking.getVoucherDiscountAmount(),
                booking.getSeatSurchargeAmount(),
                booking.getBaggageAmount(),
                booking.getTotalAmount(),
                booking.getCurrency(),
                booking.getPaymentExpiresAt(),
                booking.getCancelledAt(),
                booking.getCancelReason(),
                booking.getCreatedAt(),
                booking.getUpdatedAt(),
                voucherInfo,
                AGENCY_CONTACT,
                segmentDetails,
                passengerDetails,
                seatHoldResponses,
                baggageDetails
        );
    }

    private Specification<Booking> buildSpec(ListQuery query, String status, Long userId) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), BookingStatus.valueOf(status.trim().toUpperCase())));
            }
            String keyword = query.getKeywordOrNull();
            if (keyword != null) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("bookingCode")), like),
                        cb.like(cb.lower(root.get("orderCode")), like),
                        cb.like(cb.lower(root.get("user").get("fullName")), like),
                        cb.like(cb.lower(root.get("user").get("email")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.response.booking.HoldSeatResponse;
import com.example.domesticflightbookingwebbackend.entity.Booking;
import com.example.domesticflightbookingwebbackend.entity.BookingSegment;
import com.example.domesticflightbookingwebbackend.entity.BookingStatus;
import com.example.domesticflightbookingwebbackend.entity.FlightSeat;
import com.example.domesticflightbookingwebbackend.entity.FlightSeatStatus;
import com.example.domesticflightbookingwebbackend.entity.Passenger;
import com.example.domesticflightbookingwebbackend.entity.PassengerType;
import com.example.domesticflightbookingwebbackend.entity.SeatClass;
import com.example.domesticflightbookingwebbackend.entity.SeatHold;
import com.example.domesticflightbookingwebbackend.entity.SeatHoldStatus;
import com.example.domesticflightbookingwebbackend.entity.SeatType;
import com.example.domesticflightbookingwebbackend.entity.UserVoucherStatus;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.repository.BookingRepository;
import com.example.domesticflightbookingwebbackend.repository.BookingSegmentRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightSeatRepository;
import com.example.domesticflightbookingwebbackend.repository.PassengerRepository;
import com.example.domesticflightbookingwebbackend.repository.SeatHoldRepository;
import com.example.domesticflightbookingwebbackend.repository.UserVoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Dịch vụ Giữ ghế chống trùng 3 lớp & Job tự động nhả ghế 15 phút (Phase 3 — B3.1).
 *
 * <p>Cơ chế chống đặt ghế trùng 3 lớp (GEMINI.md mục 2.1.7 & DB v0.5 mục 5.2):
 * <ol>
 *   <li><b>Lớp 1 — Redis Distributed Lock:</b> {@code SET lock:seat:{flightSeatId} <token> NX EX 5}
 *       trước khi thao tác DB; giải phóng bằng Lua script kiểm tra đúng chủ sở hữu token.</li>
 *   <li><b>Lớp 2 — Conditional Atomic SQL Update kèm {@code @Version}:</b>
 *       {@code UPDATE flight_seats SET status = 'HOLD', version = version + 1 WHERE id = :id AND status = 'AVAILABLE'}.
 *       Chỉ khi số dòng ảnh hưởng = 1 mới cho phép tạo bản ghi {@code seat_holds}.</li>
 *   <li><b>Lớp 3 — Partial Unique Index trên PostgreSQL:</b>
 *       {@code uq_seat_holds_active_per_seat ON seat_holds (flight_seat_id) WHERE status IN ('HOLD', 'CONFIRMED')}
 *       chặn tuyệt đối ở tầng lưu trữ nếu có hai tiến trình vượt qua cùng lúc.</li>
 * </ol>
 *
 * <p>Kiểm tra nghiệp vụ bắt buộc (BR-P2-06, BR-P2-08, BR-P3-05, BR-P3-10):
 * <ul>
 *   <li>Kiểm tra {@code booking.user.id == currentUserId} (chống IDOR).</li>
 *   <li>Booking phải đang ở trạng thái {@code PENDING_PAYMENT} và chưa quá hạn 15 phút.</li>
 *   <li>Em bé ({@code INFANT}) không được chọn/giữ ghế.</li>
 *   <li>Ghế chọn phải thuộc chuyến bay nằm trong chặng ({@code booking_segments}) của đơn.</li>
 *   <li><b>Kiểm tra đúng khoang (Cabin check):</b> {@code flightSeat.aircraftSeat.seatType.seatClass}
 *       bắt buộc trùng với {@code segment.fareClass.seatClass}. Tuyệt đối không cho mua vé Phổ thông
 *       nhưng giữ ghế Thương gia.</li>
 *   <li>Nếu hành khách đã giữ một ghế khác trên cùng chuyến bay của đơn đó, tự động nhả ghế cũ
 *       trước khi giữ ghế mới và tính lại {@code seat_surcharge_amount} + {@code total_amount} của booking.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatHoldService {

    private static final String SEAT_LOCK_PREFIX = "lock:seat:";
    private static final Duration SEAT_LOCK_TTL = Duration.ofSeconds(5);
    private static final int HOLD_DURATION_MINUTES = 15;
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final SeatHoldRepository seatHoldRepository;
    private final SeatInventoryService seatInventoryService;
    private final FlightSeatRepository flightSeatRepository;
    private final BookingRepository bookingRepository;
    private final BookingSegmentRepository bookingSegmentRepository;
    private final PassengerRepository passengerRepository;
    private final UserVoucherRepository userVoucherRepository;
    private final PricingService pricingService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Giữ ghế 15 phút cho một hành khách trong đơn đặt vé (kiểm tra chủ sở hữu {@code userId}).
     */
    @Transactional
    public HoldSeatResponse hold(Long userId, Long bookingId, Long passengerId, Long flightSeatId) {
        Booking booking = findOwnedPendingBooking(userId, bookingId);
        Passenger passenger = findPassengerInBooking(bookingId, passengerId);
        if (passenger.getPassengerType() == PassengerType.INFANT) {
            throw new BusinessException("Hành khách em bé (INFANT) ngồi cùng người lớn, không được giữ ghế riêng");
        }

        FlightSeat flightSeat = flightSeatRepository.findWithDetailsById(flightSeatId)
                .orElseThrow(() -> new ResourceNotFoundException("Ghế chuyến bay không tồn tại"));

        BookingSegment matchingSegment = bookingSegmentRepository.findByBookingIdOrderBySegmentOrderAsc(bookingId)
                .stream()
                .filter(seg -> seg.getFlight().getId().equals(flightSeat.getFlight().getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Ghế đã chọn không thuộc chuyến bay nào trong đơn đặt vé này"));

        // Kiểm tra khoang ghế khớp khoang của hạng vé đã mua (BR-P2-06 / BR-P3-05)
        SeatType seatType = flightSeat.getAircraftSeat().getSeatType();
        SeatClass seatCabin = seatType.getSeatClass();
        SeatClass purchasedCabin = matchingSegment.getFareClass().getSeatClass();
        if (seatCabin != purchasedCabin) {
            throw new BusinessException("Hạng vé đã mua thuộc khoang " + purchasedCabin
                    + " — không thể chọn ghế thuộc khoang " + seatCabin);
        }

        // Nếu hành khách đã giữ một ghế khác trên cùng chuyến bay này, nhả ghế cũ trước
        List<SeatHold> existingHolds = seatHoldRepository.findByBookingIdAndStatusIn(
                bookingId, List.of(SeatHoldStatus.HOLD));
        for (SeatHold oldHold : existingHolds) {
            if (oldHold.getPassenger().getId().equals(passengerId)
                    && oldHold.getFlightSeat().getFlight().getId().equals(flightSeat.getFlight().getId())) {
                if (oldHold.getFlightSeat().getId().equals(flightSeatId)) {
                    // Đã giữ đúng ghế này rồi -> trả về luôn (idempotent)
                    return toResponse(oldHold);
                }
                releaseInternal(oldHold);
            }
        }

        // LỚP 1: Redis Distributed Lock (SET lock:seat:{flightSeatId} token NX EX 5)
        String lockKey = SEAT_LOCK_PREFIX + flightSeatId;
        String lockToken = UUID.randomUUID().toString();
        boolean lockAcquired = tryAcquireRedisLock(lockKey, lockToken);
        if (!lockAcquired) {
            throw new BusinessException("Ghế " + flightSeat.getSeatCode()
                    + " đang có khách hàng khác thao tác chọn — vui lòng chọn ghế khác");
        }

        try {
            // LỚP 2: Conditional Atomic SQL Update kèm @Version
            if (!seatInventoryService.hold(flightSeatId)) {
                throw new BusinessException("Ghế " + flightSeat.getSeatCode()
                        + " đã có người giữ hoặc đã được đặt — vui lòng chọn ghế khác");
            }

            // LỚP 3: Lưu vào seat_holds (được bảo vệ bởi Partial Unique Index uq_seat_holds_active_per_seat)
            SeatHold seatHold = new SeatHold();
            seatHold.setBooking(booking);
            seatHold.setPassenger(passenger);
            seatHold.setFlightSeat(flightSeat);
            LocalDateTime expiresAt = booking.getPaymentExpiresAt() != null
                    ? booking.getPaymentExpiresAt()
                    : LocalDateTime.now().plusMinutes(HOLD_DURATION_MINUTES);
            seatHold.setHoldExpiresAt(expiresAt);
            BigDecimal surcharge = flightSeat.getSeatSurcharge() != null
                    ? flightSeat.getSeatSurcharge().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            seatHold.setSurchargeAtBooking(surcharge);
            seatHold.setStatus(SeatHoldStatus.HOLD);

            SeatHold saved;
            try {
                saved = seatHoldRepository.saveAndFlush(seatHold);
            } catch (DataIntegrityViolationException ex) {
                throw new BusinessException("Ghế " + flightSeat.getSeatCode() + " vừa được người khác giữ");
            }

            recalculateBookingSeatSurcharge(booking);
            log.info("Đã giữ ghế {} (flightSeatId={}) cho hành khách id={} trong booking {}",
                    flightSeat.getSeatCode(), flightSeatId, passengerId, booking.getBookingCode());
            return toResponse(saved);
        } finally {
            releaseRedisLock(lockKey, lockToken);
        }
    }

    /**
     * Nhả một lượt giữ ghế theo yêu cầu của khách ({@code DELETE /api/bookings/{bookingId}/seats/hold/{seatHoldId}}).
     */
    @Transactional
    public void release(Long userId, Long bookingId, Long seatHoldId) {
        Booking booking = findOwnedPendingBooking(userId, bookingId);
        SeatHold seatHold = seatHoldRepository.findById(seatHoldId)
                .filter(sh -> sh.getBooking().getId().equals(bookingId))
                .orElseThrow(() -> new ResourceNotFoundException("Lượt giữ ghế không tồn tại trong đơn đặt vé này"));

        if (seatHold.getStatus() == SeatHoldStatus.RELEASED) {
            return;
        }
        if (seatHold.getStatus() == SeatHoldStatus.CONFIRMED) {
            throw new BusinessException("Ghế đã được xác nhận xuất vé — không thể nhả lẻ");
        }

        releaseInternal(seatHold);
        recalculateBookingSeatSurcharge(booking);
        log.info("Đã nhả ghế giữ id={} của booking {}", seatHoldId, booking.getBookingCode());
    }

    /**
     * Xác nhận toàn bộ ghế đang HOLD của đơn đặt vé sang CONFIRMED và ghế vật lý sang BOOKED
     * (gọi khi thanh toán thành công ở Phase 3B).
     */
    @Transactional
    public void confirmAll(Long bookingId) {
        List<SeatHold> holds = seatHoldRepository.findByBookingIdAndStatusIn(
                bookingId, List.of(SeatHoldStatus.HOLD));
        for (SeatHold hold : holds) {
            hold.setStatus(SeatHoldStatus.CONFIRMED);
            FlightSeat fs = hold.getFlightSeat();
            fs.setStatus(FlightSeatStatus.BOOKED);
            flightSeatRepository.save(fs);
        }
        seatHoldRepository.saveAll(holds);
    }

    /**
     * Giải phóng toàn bộ ghế đang HOLD hoặc CONFIRMED của một booking khi hủy đơn hoặc hết hạn.
     */
    @Transactional
    public void releaseAllForBooking(Long bookingId) {
        List<SeatHold> activeHolds = seatHoldRepository.findByBookingIdAndStatusIn(
                bookingId, List.of(SeatHoldStatus.HOLD, SeatHoldStatus.CONFIRMED));
        for (SeatHold hold : activeHolds) {
            releaseInternal(hold);
        }
    }

    /**
     * Job định kỳ chạy mỗi 60 giây:
     * 1. Quét các đơn {@code PENDING_PAYMENT} đã quá hạn {@code payment_expires_at} (15 phút),
     *    đổi sang {@code EXPIRED}, giải phóng toàn bộ ghế đang giữ và trả voucher {@code RESERVED} về {@code AVAILABLE}.
     * 2. Quét các bản ghi {@code seat_holds} lẻ đang {@code HOLD} đã quá {@code hold_expires_at} để nhả ghế về {@code AVAILABLE}.
     *
     * @return tổng số lượt giữ ghế đã được giải phóng
     */
    @Scheduled(fixedDelayString = "${app.booking.expire-scan-ms:60000}")
    @Transactional
    public int releaseExpired() {
        LocalDateTime now = LocalDateTime.now();
        int releasedSeats = 0;

        // 1. Xử lý các booking chờ thanh toán quá 15 phút
        List<Booking> expiredBookings = bookingRepository.findByStatusAndPaymentExpiresAtBefore(
                BookingStatus.PENDING_PAYMENT, now);
        for (Booking booking : expiredBookings) {
            booking.setStatus(BookingStatus.EXPIRED);
            booking.setUpdatedAt(now);
            bookingRepository.save(booking);

            List<SeatHold> holds = seatHoldRepository.findByBookingIdAndStatusIn(
                    booking.getId(), List.of(SeatHoldStatus.HOLD));
            for (SeatHold hold : holds) {
                releaseInternal(hold);
                releasedSeats++;
            }

            // Trả voucher đang RESERVED về lại AVAILABLE nếu còn hạn
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
            log.info("Đơn đặt vé {} đã quá hạn 15 phút -> chuyển sang EXPIRED", booking.getBookingCode());
        }

        // 2. Xử lý các seat_holds lẻ quá hạn
        List<SeatHold> expiredHolds = seatHoldRepository.findByStatusAndHoldExpiresAtBefore(
                SeatHoldStatus.HOLD, now);
        for (SeatHold hold : expiredHolds) {
            releaseInternal(hold);
            releasedSeats++;
            Booking b = hold.getBooking();
            if (b != null && b.getStatus() == BookingStatus.PENDING_PAYMENT) {
                recalculateBookingSeatSurcharge(b);
            }
        }

        if (releasedSeats > 0) {
            log.info("Job nhả ghế định kỳ đã giải phóng {} ghế quá hạn", releasedSeats);
        }
        return releasedSeats;
    }

    public HoldSeatResponse toResponse(SeatHold hold) {
        FlightSeat fs = hold.getFlightSeat();
        SeatType st = fs.getAircraftSeat().getSeatType();
        return new HoldSeatResponse(
                hold.getId(),
                hold.getBooking().getId(),
                hold.getPassenger().getId(),
                hold.getPassenger().getFullName(),
                fs.getFlight().getId(),
                fs.getFlight().getFlightNumber(),
                fs.getId(),
                fs.getSeatCode(),
                st.getSeatClass().name(),
                st.getSeatTypeCode(),
                st.getSeatTypeName(),
                hold.getSurchargeAtBooking(),
                hold.getStatus().name(),
                hold.getHoldExpiresAt(),
                hold.getCreatedAt()
        );
    }

    // ================= Helpers =================

    private Booking findOwnedPendingBooking(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findWithUserById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn đặt vé không tồn tại"));
        if (!booking.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền thao tác trên đơn đặt vé này");
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException("Chỉ có thể chọn hoặc đổi ghế khi đơn đặt vé đang ở trạng thái PENDING_PAYMENT");
        }
        if (booking.getPaymentExpiresAt() != null && booking.getPaymentExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Đơn đặt vé đã hết thời hạn giữ chỗ 15 phút");
        }
        return booking;
    }

    private Passenger findPassengerInBooking(Long bookingId, Long passengerId) {
        return passengerRepository.findById(passengerId)
                .filter(p -> p.getBooking().getId().equals(bookingId))
                .orElseThrow(() -> new ResourceNotFoundException("Hành khách không thuộc đơn đặt vé này"));
    }

    private void releaseInternal(SeatHold hold) {
        hold.setStatus(SeatHoldStatus.RELEASED);
        seatHoldRepository.saveAndFlush(hold);

        FlightSeat fs = hold.getFlightSeat();
        if (fs.getStatus() == FlightSeatStatus.HOLD) {
            seatInventoryService.release(fs.getId());
        } else if (fs.getStatus() == FlightSeatStatus.BOOKED) {
            fs.setStatus(FlightSeatStatus.AVAILABLE);
            flightSeatRepository.save(fs);
        }
    }

    private void recalculateBookingSeatSurcharge(Booking booking) {
        List<SeatHold> activeHolds = seatHoldRepository.findByBookingIdAndStatusIn(
                booking.getId(), List.of(SeatHoldStatus.HOLD, SeatHoldStatus.CONFIRMED));
        BigDecimal totalSurcharge = activeHolds.stream()
                .map(SeatHold::getSurchargeAtBooking)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        booking.setSeatSurchargeAmount(totalSurcharge);
        booking.setTotalAmount(pricingService.computeTotal(booking));
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);
    }

    private boolean tryAcquireRedisLock(String lockKey, String lockToken) {
        if (stringRedisTemplate == null) {
            return true;
        }
        try {
            Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockToken, SEAT_LOCK_TTL);
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            log.warn("Không thể kết nối Redis để lấy lock {} — fallback về khóa nguyên tử DB (@Version): {}",
                    lockKey, e.getMessage());
            return true;
        }
    }

    private void releaseRedisLock(String lockKey, String lockToken) {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), lockToken);
        } catch (Exception e) {
            log.debug("Bỏ qua lỗi giải phóng Redis lock {}: {}", lockKey, e.getMessage());
        }
    }
}

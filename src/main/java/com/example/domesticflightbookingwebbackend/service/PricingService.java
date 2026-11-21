package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.config.PricingProperties;
import com.example.domesticflightbookingwebbackend.entity.Booking;
import com.example.domesticflightbookingwebbackend.entity.BookingSegment;
import com.example.domesticflightbookingwebbackend.entity.DiscountType;
import com.example.domesticflightbookingwebbackend.entity.FareClass;
import com.example.domesticflightbookingwebbackend.entity.Flight;
import com.example.domesticflightbookingwebbackend.entity.FlightFare;
import com.example.domesticflightbookingwebbackend.entity.Passenger;
import com.example.domesticflightbookingwebbackend.entity.PassengerType;
import com.example.domesticflightbookingwebbackend.entity.Promotion;
import com.example.domesticflightbookingwebbackend.entity.PromotionScope;
import com.example.domesticflightbookingwebbackend.entity.TripType;
import com.example.domesticflightbookingwebbackend.entity.TripTypeCondition;
import com.example.domesticflightbookingwebbackend.entity.UserVoucher;
import com.example.domesticflightbookingwebbackend.entity.UserVoucherStatus;
import com.example.domesticflightbookingwebbackend.entity.Voucher;
import com.example.domesticflightbookingwebbackend.entity.VoucherScope;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.repository.FlightFareRepository;
import com.example.domesticflightbookingwebbackend.repository.PromotionExcludedFlightRepository;
import com.example.domesticflightbookingwebbackend.repository.PromotionRepository;
import com.example.domesticflightbookingwebbackend.repository.PromotionScopeRepository;
import com.example.domesticflightbookingwebbackend.repository.VoucherExcludedFlightRepository;
import com.example.domesticflightbookingwebbackend.repository.VoucherScopeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dịch vụ Tính giá đơn đặt vé (Phase 3 — B3.2, DB v0.5 mục 5.3).
 *
 * <p>Công thức 4 bước bắt buộc:
 * <ol>
 *   <li><b>Bước 1 — Giá từng dòng vé:</b> ADULT = {@code base_fare + tax_fee};
 *       CHILD = ADULT × 75%; INFANT = ADULT × 10% (làm tròn {@code HALF_UP}).</li>
 *   <li><b>Bước 2 — Khuyến mãi tự động (Promotion):</b> Áp cho các dòng vé ADULT và CHILD
 *       (<b>bỏ qua INFANT</b>). Lọc các khuyến mãi đang hiệu lực, khớp {@code applicable_trip_type},
 *       khớp {@code promotion_scopes} (nếu không có scope nào thì áp dụng toàn hệ thống), không nằm trong
 *       {@code promotion_excluded_flights}, và giá dòng vé ≥ {@code min_fare_amount}.
 *       Chọn chương trình khuyến mãi giảm được nhiều tiền nhất cho chặng đó.</li>
 *   <li><b>Bước 3 — Voucher từ ví khách:</b> Chỉ áp trên phần tiền vé sau khuyến mãi của các dòng
 *       ADULT và CHILD (<b>không tính dòng INFANT</b>) thuộc các chặng khớp {@code voucher_scopes} và
 *       không nằm trong {@code voucher_excluded_flights}. Phải đạt {@code min_order_amount},
 *       trần giảm {@code max_discount_amount}.</li>
 *   <li><b>Bước 4 — Tổng thanh toán:</b>
 *       {@code total_amount = fare_amount − promotion_discount_amount − voucher_discount_amount + seat_surcharge_amount + baggage_amount}.
 *       Phụ thu ghế và hành lý tuyệt đối không bị giảm bởi khuyến mãi hay voucher.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final FlightFareRepository flightFareRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionScopeRepository promotionScopeRepository;
    private final PromotionExcludedFlightRepository promotionExcludedFlightRepository;
    private final VoucherScopeRepository voucherScopeRepository;
    private final VoucherExcludedFlightRepository voucherExcludedFlightRepository;
    private final PricingProperties pricingProperties;

    /** Kết quả tính giá và khuyến mãi tốt nhất cho một chặng bay. */
    public record SegmentPricingResult(
            BigDecimal rawFareAmount,
            BigDecimal adultChildRawFare,
            Promotion bestPromotion,
            BigDecimal promotionDiscountAmount
    ) {
    }

    /**
     * Bước 1: Tính giá dòng vé cho một hành khách trên một chuyến bay với hạng vé đã chọn.
     */
    public BigDecimal priceTicket(Flight flight, FareClass fareClass, PassengerType passengerType) {
        FlightFare activeFare = resolveActiveFare(flight.getId(), fareClass.getId());
        return priceTicketFromFare(activeFare, passengerType);
    }

    public BigDecimal priceTicketFromFare(FlightFare fare, PassengerType passengerType) {
        BigDecimal adultPrice = fare.getBaseFare()
                .add(fare.getTaxFee() == null ? BigDecimal.ZERO : fare.getTaxFee())
                .setScale(2, RoundingMode.HALF_UP);

        return switch (passengerType) {
            case ADULT -> adultPrice;
            case CHILD -> computePercentage(adultPrice, pricingProperties.getChildFarePercent());
            case INFANT -> computePercentage(adultPrice, pricingProperties.getInfantFarePercent());
        };
    }

    /**
     * Bước 1 + Bước 2: Tính tổng giá vé gốc của một chặng cho danh sách hành khách
     * và tự động chọn chương trình khuyến mãi (Promotion) giảm nhiều nhất (bỏ qua INFANT).
     */
    public SegmentPricingResult priceSegmentWithBestPromotion(
            Flight flight,
            FareClass fareClass,
            TripType tripType,
            List<Passenger> passengers) {
        FlightFare activeFare = resolveActiveFare(flight.getId(), fareClass.getId());

        if (passengers == null || passengers.isEmpty()) {
            // Mặc định 1 người lớn nếu lúc tạo đơn chưa truyền danh sách hành khách chi tiết
            BigDecimal oneAdult = priceTicketFromFare(activeFare, PassengerType.ADULT);
            return findBestPromotionForSegment(
                    flight, fareClass, tripType,
                    List.of(new TicketLine(PassengerType.ADULT, oneAdult)),
                    oneAdult, oneAdult);
        }

        BigDecimal rawTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal adultChildRaw = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<TicketLine> lines = passengers.stream()
                .map(p -> new TicketLine(p.getPassengerType(), priceTicketFromFare(activeFare, p.getPassengerType())))
                .toList();

        for (TicketLine line : lines) {
            rawTotal = rawTotal.add(line.fareAmount());
            if (line.passengerType() != PassengerType.INFANT) {
                adultChildRaw = adultChildRaw.add(line.fareAmount());
            }
        }

        return findBestPromotionForSegment(flight, fareClass, tripType, lines, rawTotal, adultChildRaw);
    }

    /**
     * Bước 3: Tính số tiền giảm của Voucher từ ví khách trên đơn đặt vé
     * (chỉ tính trên phần giá vé ADULT + CHILD sau khi đã trừ khuyến mãi của các chặng hợp lệ).
     */
    public BigDecimal applyVoucher(
            Booking booking,
            List<BookingSegment> segments,
            List<Passenger> passengers,
            UserVoucher userVoucher) {
        if (userVoucher == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        Voucher voucher = userVoucher.getVoucher();
        validateVoucherUsable(booking, userVoucher, voucher);

        List<VoucherScope> scopes = voucherScopeRepository.findByVoucherId(voucher.getId());
        Set<Long> excludedFlightIds = voucherExcludedFlightRepository.findByVoucherId(voucher.getId()).stream()
                .map(e -> e.getFlight().getId())
                .collect(Collectors.toSet());

        // Tính cơ sở áp voucher: tổng (giá ADULT/CHILD của chặng - giảm khuyến mãi của chặng)
        // cho các chặng khớp phạm vi của voucher
        BigDecimal eligibleAfterPromo = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (BookingSegment seg : segments) {
            Flight flight = seg.getFlight();
            FareClass fc = seg.getFareClass();
            if (!matchesVoucherScope(flight, fc, scopes, excludedFlightIds)) {
                continue;
            }
            BigDecimal segAdultChildRaw = computeAdultChildRawForSegment(flight, fc, passengers, seg);
            BigDecimal segPromoDiscount = seg.getPromotionDiscountAmount() == null
                    ? BigDecimal.ZERO
                    : seg.getPromotionDiscountAmount();
            BigDecimal segNetAdultChild = segAdultChildRaw.subtract(segPromoDiscount).max(BigDecimal.ZERO);
            eligibleAfterPromo = eligibleAfterPromo.add(segNetAdultChild);
        }

        if (eligibleAfterPromo.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Voucher không áp dụng được cho chặng bay hoặc hạng vé đã chọn");
        }
        if (voucher.getMinOrderAmount() != null
                && eligibleAfterPromo.compareTo(voucher.getMinOrderAmount()) < 0) {
            throw new BusinessException("Đơn đặt vé chưa đạt giá trị tối thiểu ("
                    + voucher.getMinOrderAmount() + " VND) để áp dụng voucher này");
        }

        BigDecimal discount = computeDiscount(
                eligibleAfterPromo,
                voucher.getDiscountType(),
                voucher.getDiscountValue(),
                voucher.getMaxDiscountAmount());
        return discount.min(eligibleAfterPromo).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Bước 4: Tính tổng tiền khách phải thanh toán theo đúng công thức CHECK constraint trong DB:
     * {@code total_amount = fare_amount − promotion_discount_amount − voucher_discount_amount + seat_surcharge_amount + baggage_amount}.
     */
    public BigDecimal computeTotal(Booking booking) {
        BigDecimal fare = nullSafe(booking.getFareAmount());
        BigDecimal promo = nullSafe(booking.getPromotionDiscountAmount());
        BigDecimal voucher = nullSafe(booking.getVoucherDiscountAmount());
        BigDecimal seat = nullSafe(booking.getSeatSurchargeAmount());
        BigDecimal baggage = nullSafe(booking.getBaggageAmount());

        BigDecimal ticketNet = fare.subtract(promo).subtract(voucher);
        if (ticketNet.compareTo(BigDecimal.ZERO) < 0) {
            ticketNet = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return ticketNet.add(seat).add(baggage).setScale(2, RoundingMode.HALF_UP);
    }

    // ================= Helpers =================

    public record TicketLine(PassengerType passengerType, BigDecimal fareAmount) {
    }

    private FlightFare resolveActiveFare(Long flightId, Long fareClassId) {
        return flightFareRepository.findActiveFaresByFlightAndFareClass(flightId, fareClassId, LocalDateTime.now())
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "Chuyến bay chưa có bảng giá đang hiệu lực cho hạng vé đã chọn"));
    }

    private SegmentPricingResult findBestPromotionForSegment(
            Flight flight,
            FareClass fareClass,
            TripType tripType,
            List<TicketLine> lines,
            BigDecimal rawTotal,
            BigDecimal adultChildRaw) {
        LocalDate today = LocalDate.now();
        List<Promotion> activePromotions = promotionRepository
                .findByIsActiveTrueAndIsDeletedFalseAndStartDateLessThanEqualAndEndDateGreaterThanEqual(today, today);

        Promotion bestPromo = null;
        BigDecimal bestDiscount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (Promotion promo : activePromotions) {
            if (!matchesTripType(promo.getApplicableTripType(), tripType)) {
                continue;
            }
            Set<Long> excludedFlights = promotionExcludedFlightRepository.findByPromotionId(promo.getId()).stream()
                    .map(e -> e.getFlight().getId())
                    .collect(Collectors.toSet());
            List<PromotionScope> scopes = promotionScopeRepository.findByPromotionId(promo.getId());
            if (!matchesPromotionScope(flight, fareClass, scopes, excludedFlights)) {
                continue;
            }

            BigDecimal promoTotalDiscount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            for (TicketLine line : lines) {
                // Quy tắc bắt buộc: Em bé (INFANT) KHÔNG được hưởng khuyến mãi
                if (line.passengerType() == PassengerType.INFANT) {
                    continue;
                }
                if (promo.getMinFareAmount() != null
                        && line.fareAmount().compareTo(promo.getMinFareAmount()) < 0) {
                    continue;
                }
                BigDecimal lineDisc = computeDiscount(
                        line.fareAmount(),
                        promo.getDiscountType(),
                        promo.getDiscountValue(),
                        promo.getMaxDiscountAmount());
                promoTotalDiscount = promoTotalDiscount.add(lineDisc);
            }

            if (promoTotalDiscount.compareTo(bestDiscount) > 0) {
                bestDiscount = promoTotalDiscount;
                bestPromo = promo;
            }
        }

        return new SegmentPricingResult(rawTotal, adultChildRaw, bestPromo, bestDiscount);
    }

    private BigDecimal computeAdultChildRawForSegment(
            Flight flight,
            FareClass fareClass,
            List<Passenger> passengers,
            BookingSegment segment) {
        if (passengers == null || passengers.isEmpty()) {
            return segment.getFareAmountAtBooking() == null
                    ? BigDecimal.ZERO
                    : segment.getFareAmountAtBooking();
        }
        FlightFare activeFare = flightFareRepository
                .findActiveFaresByFlightAndFareClass(flight.getId(), fareClass.getId(), LocalDateTime.now())
                .stream()
                .findFirst()
                .orElse(null);
        if (activeFare == null) {
            return segment.getFareAmountAtBooking();
        }
        BigDecimal sum = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (Passenger p : passengers) {
            if (p.getPassengerType() != PassengerType.INFANT) {
                sum = sum.add(priceTicketFromFare(activeFare, p.getPassengerType()));
            }
        }
        return sum;
    }

    private void validateVoucherUsable(Booking booking, UserVoucher userVoucher, Voucher voucher) {
        if (userVoucher.getStatus() == UserVoucherStatus.USED
                || userVoucher.getStatus() == UserVoucherStatus.EXPIRED) {
            throw new BusinessException("Voucher trong ví đã được sử dụng hoặc đã hết hạn");
        }
        if (userVoucher.getStatus() == UserVoucherStatus.RESERVED
                && userVoucher.getBooking() != null
                && !userVoucher.getBooking().getId().equals(booking.getId())) {
            throw new BusinessException("Voucher này đang được giữ ở một đơn đặt vé khác");
        }
        if (userVoucher.getExpiresAt() != null && userVoucher.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Voucher trong ví đã hết hạn sử dụng");
        }
        LocalDate today = LocalDate.now();
        if (Boolean.TRUE.equals(voucher.getIsDeleted())
                || !Boolean.TRUE.equals(voucher.getIsActive())
                || today.isBefore(voucher.getStartDate())
                || today.isAfter(voucher.getEndDate())) {
            throw new BusinessException("Chương trình voucher hiện không còn hiệu lực");
        }
        if (!matchesTripType(voucher.getApplicableTripType(), booking.getTripType())) {
            throw new BusinessException("Voucher chỉ áp dụng cho hành trình " + voucher.getApplicableTripType());
        }
    }

    private boolean matchesTripType(TripTypeCondition condition, TripType tripType) {
        if (condition == null || condition == TripTypeCondition.ANY) {
            return true;
        }
        return condition.name().equals(tripType.name());
    }

    private boolean matchesPromotionScope(
            Flight flight,
            FareClass fareClass,
            List<PromotionScope> scopes,
            Set<Long> excludedFlightIds) {
        if (excludedFlightIds.contains(flight.getId())) {
            return false;
        }
        if (scopes == null || scopes.isEmpty()) {
            return true;
        }
        for (PromotionScope s : scopes) {
            if (matchesSingleScope(
                    flight, fareClass, s.getTargetType(),
                    s.getAirline() == null ? null : s.getAirline().getId(),
                    s.getFareClass() == null ? null : s.getFareClass().getId(),
                    s.getFlight() == null ? null : s.getFlight().getId(),
                    s.getDepartureAirport() == null ? null : s.getDepartureAirport().getId(),
                    s.getArrivalAirport() == null ? null : s.getArrivalAirport().getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesVoucherScope(
            Flight flight,
            FareClass fareClass,
            List<VoucherScope> scopes,
            Set<Long> excludedFlightIds) {
        if (excludedFlightIds.contains(flight.getId())) {
            return false;
        }
        if (scopes == null || scopes.isEmpty()) {
            return true;
        }
        for (VoucherScope s : scopes) {
            if (matchesSingleScope(
                    flight, fareClass, s.getTargetType(),
                    s.getAirline() == null ? null : s.getAirline().getId(),
                    s.getFareClass() == null ? null : s.getFareClass().getId(),
                    s.getFlight() == null ? null : s.getFlight().getId(),
                    s.getDepartureAirport() == null ? null : s.getDepartureAirport().getId(),
                    s.getArrivalAirport() == null ? null : s.getArrivalAirport().getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesSingleScope(
            Flight flight,
            FareClass fareClass,
            com.example.domesticflightbookingwebbackend.entity.ScopeTargetType targetType,
            Long airlineId,
            Long fareClassId,
            Long flightId,
            Long departureAirportId,
            Long arrivalAirportId) {
        if (targetType == null) {
            return true;
        }
        return switch (targetType) {
            case AIRLINE -> airlineId != null && airlineId.equals(flight.getAirline().getId());
            case FARE_CLASS -> fareClassId != null && fareClassId.equals(fareClass.getId());
            case FLIGHT -> flightId != null && flightId.equals(flight.getId());
            case ROUTE -> departureAirportId != null
                    && departureAirportId.equals(flight.getDepartureAirport().getId())
                    && (arrivalAirportId == null || arrivalAirportId.equals(flight.getArrivalAirport().getId()));
        };
    }

    private BigDecimal computeDiscount(
            BigDecimal baseAmount,
            DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal maxDiscountAmount) {
        if (baseAmount == null || baseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal disc;
        if (discountType == DiscountType.PERCENT) {
            disc = computePercentage(baseAmount, discountValue);
            if (maxDiscountAmount != null && disc.compareTo(maxDiscountAmount) > 0) {
                disc = maxDiscountAmount;
            }
        } else {
            disc = discountValue;
            if (maxDiscountAmount != null && disc.compareTo(maxDiscountAmount) > 0) {
                disc = maxDiscountAmount;
            }
        }
        return disc.min(baseAmount).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal computePercentage(BigDecimal amount, int percent) {
        return amount
                .multiply(BigDecimal.valueOf(percent))
                .divide(HUNDRED, 0, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal computePercentage(BigDecimal amount, BigDecimal percent) {
        return amount
                .multiply(percent)
                .divide(HUNDRED, 0, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nullSafe(BigDecimal val) {
        return val == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : val.setScale(2, RoundingMode.HALF_UP);
    }
}

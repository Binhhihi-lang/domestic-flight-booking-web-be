package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.config.PricingProperties;
import com.example.domesticflightbookingwebbackend.dto.request.flight.FlightFareRequest;
import com.example.domesticflightbookingwebbackend.dto.response.flight.FlightFareResponse;
import com.example.domesticflightbookingwebbackend.dto.response.flight.PricedFareResponse;
import com.example.domesticflightbookingwebbackend.entity.FareClass;
import com.example.domesticflightbookingwebbackend.entity.FareRule;
import com.example.domesticflightbookingwebbackend.entity.Flight;
import com.example.domesticflightbookingwebbackend.entity.FlightFare;
import com.example.domesticflightbookingwebbackend.entity.SeatClass;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.mapper.FlightFareMapper;
import com.example.domesticflightbookingwebbackend.repository.FareClassRepository;
import com.example.domesticflightbookingwebbackend.repository.FareRuleRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightFareRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightSeatRepository;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Giá bán theo chuyến (Phase 2 — B2.2).
 *
 * <p>Quy tắc nghiệp vụ:
 * <ul>
 *   <li>Bảng {@code flight_fares} chỉ lưu giá người lớn ({@code base_fare + tax_fee}).</li>
 *   <li>Giá trẻ em = {@code childFarePercent}% (75%), em bé = {@code infantFarePercent}% (10%)
 *       cấu hình ở {@link PricingProperties}, làm tròn đến đồng ({@code HALF_UP}).</li>
 *   <li>Không cho hai dòng chồng khoảng thời gian hiệu lực {@code [effective_from, effective_to)}
 *       cùng {@code (flight_id, fare_class_id)} → lỗi 1006.</li>
 *   <li>Hạng vé phải thuộc cùng hãng hàng không với chuyến bay → lỗi 1006.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightFareService {

    private static final String[] SORT_WHITELIST = {"base_fare", "tax_fee", "effective_from", "created_at"};
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final FlightFareRepository flightFareRepository;
    private final FlightRepository flightRepository;
    private final FareClassRepository fareClassRepository;
    private final FareRuleRepository fareRuleRepository;
    private final FlightSeatRepository flightSeatRepository;
    private final PricingProperties pricingProperties;
    private final FlightFareMapper flightFareMapper;

    @Transactional(readOnly = true)
    public PageData<FlightFareResponse> list(ListQuery query, Long flightId, Long fareClassId) {
        Pageable pageable = PageMapper.toPageable(query, "effectiveFrom", SORT_WHITELIST);
        Page<FlightFare> page = flightFareRepository.findAll(buildSpec(query, flightId, fareClassId), pageable);
        return PageMapper.toPageData(page, this::toResponse);
    }

    @Transactional(readOnly = true)
    public FlightFareResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public FlightFareResponse create(FlightFareRequest request) {
        Flight flight = resolveFlight(request.flightId());
        FareClass fareClass = resolveFareClass(request.fareClassId());
        validateAirlineMatch(flight, fareClass);

        LocalDateTime effectiveFrom = request.effectiveFrom() != null
                ? request.effectiveFrom()
                : LocalDateTime.now();
        LocalDateTime effectiveTo = request.effectiveTo();
        validateEffectiveRange(effectiveFrom, effectiveTo);
        validateNoOverlap(flight.getId(), fareClass.getId(), effectiveFrom, effectiveTo, null);

        FlightFare fare = new FlightFare();
        fare.setFlight(flight);
        fare.setFareClass(fareClass);
        applyMutableFields(fare, request, effectiveFrom, effectiveTo);

        FlightFare saved = flightFareRepository.save(fare);
        log.info("Tạo giá vé id={} cho chuyến {} hạng {}",
                saved.getId(), flight.getFlightNumber(), fareClass.getFareClassCode());
        return toResponse(saved);
    }

    @Transactional
    public FlightFareResponse update(Long id, FlightFareRequest request) {
        FlightFare fare = findOrThrow(id);
        Flight flight = fare.getFlight();
        FareClass fareClass = fare.getFareClass();

        LocalDateTime effectiveFrom = request.effectiveFrom() != null
                ? request.effectiveFrom()
                : fare.getEffectiveFrom();
        LocalDateTime effectiveTo = request.effectiveTo();
        validateEffectiveRange(effectiveFrom, effectiveTo);
        validateNoOverlap(flight.getId(), fareClass.getId(), effectiveFrom, effectiveTo, fare.getId());

        applyMutableFields(fare, request, effectiveFrom, effectiveTo);
        FlightFare saved = flightFareRepository.save(fare);
        log.info("Cập nhật giá vé id={} thành công", id);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        FlightFare fare = findOrThrow(id);
        flightFareRepository.delete(fare);
        log.info("Xóa giá vé id={}", id);
    }

    /**
     * Lấy danh sách hạng giá đang hiệu lực của một chuyến bay, kèm giá 3 đối tượng khách,
     * số ghế trống theo khoang và chính sách hoàn vé hiện hành.
     */
    @Transactional(readOnly = true)
    public List<PricedFareResponse> getPricedFares(Long flightId) {
        Map<Long, List<PricedFareResponse>> map = getPricedFaresByFlightIds(List.of(flightId));
        return map.getOrDefault(flightId, List.of());
    }

    /**
     * Nạp hàng loạt giá đang hiệu lực, số ghế trống theo khoang và quy định hoàn vé cho tập chuyến bay
     * chỉ bằng 3 câu query (chống N+1 khi tìm kiếm hàng trăm/nghìn chuyến bay).
     */
    @Transactional(readOnly = true)
    public Map<Long, List<PricedFareResponse>> getPricedFaresByFlightIds(Collection<Long> flightIds) {
        if (flightIds == null || flightIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LocalDateTime now = LocalDateTime.now();

        List<FlightFare> activeFares = flightFareRepository.findActiveFaresByFlightIds(flightIds, now);
        if (activeFares.isEmpty()) {
            return Collections.emptyMap();
        }

        // 1. Đếm ghế trống theo (flightId, seatClass)
        Map<String, Long> cabinAvailability = new HashMap<>();
        Set<Long> flightsWithInventory = new HashSet<>();
        for (FlightSeatRepository.FlightCabinAvailability row
                : flightSeatRepository.countAvailableSeatsByFlightIdsAndCabin(flightIds)) {
            cabinAvailability.put(cabinKey(row.getFlightId(), row.getSeatClass()), row.getAvailableCount());
            flightsWithInventory.add(row.getFlightId());
        }

        // 2. Lấy FareRule mới nhất cho mỗi fareClassId
        List<Long> fareClassIds = activeFares.stream()
                .map(ff -> ff.getFareClass().getId())
                .distinct()
                .toList();
        Map<Long, FareRule> latestRuleByFareClass = new HashMap<>();
        for (FareRule rule : fareRuleRepository.findActiveRulesByFareClassIds(fareClassIds, now)) {
            latestRuleByFareClass.putIfAbsent(rule.getFareClass().getId(), rule);
        }

        // 3. Gom theo flightId
        Map<Long, List<PricedFareResponse>> result = new HashMap<>();
        for (FlightFare ff : activeFares) {
            Long fId = ff.getFlight().getId();
            FareClass fc = ff.getFareClass();
            long seatsAvail;
            if (flightsWithInventory.contains(fId)) {
                seatsAvail = cabinAvailability.getOrDefault(cabinKey(fId, fc.getSeatClass()), 0L);
            } else {
                // Chuyến mở bán xa chưa materialize flight_seats: ước tính ghế trống theo khoang chuẩn của tàu bay
                seatsAvail = switch (fc.getSeatClass()) {
                    case BUSINESS -> 18L;
                    case PREMIUM_ECONOMY -> 18L;
                    case ECONOMY -> 144L;
                };
            }
            FareRule rule = latestRuleByFareClass.get(fc.getId());

            PricedFareResponse priced = computePassengerPrices(ff, seatsAvail, rule);
            result.computeIfAbsent(fId, k -> new ArrayList<>()).add(priced);
        }
        return result;
    }

    /** Tính giá người lớn, trẻ em (75%) và em bé (10%) từ một {@link FlightFare}. */
    public PricedFareResponse computePassengerPrices(FlightFare fare, long seatsAvailable, FareRule activeRule) {
        BigDecimal adultPrice = computeAdultPrice(fare.getBaseFare(), fare.getTaxFee());
        BigDecimal childPrice = computePercentagePrice(adultPrice, pricingProperties.getChildFarePercent());
        BigDecimal infantPrice = computePercentagePrice(adultPrice, pricingProperties.getInfantFarePercent());

        FareClass fc = fare.getFareClass();
        boolean refundable = activeRule != null && Boolean.TRUE.equals(activeRule.getIsRefundable());
        Integer minHours = activeRule == null ? 0 : activeRule.getRefundMinHoursBeforeDeparture();
        BigDecimal refundPercent = activeRule == null ? BigDecimal.ZERO : activeRule.getRefundPercent();
        String refundDesc = activeRule == null ? null : activeRule.getDescription();

        return new PricedFareResponse(
                fare.getId(),
                fc.getId(),
                fc.getFareClassCode(),
                fc.getFareClassName(),
                fc.getSeatClass().name(),
                fare.getBaseFare(),
                fare.getTaxFee(),
                adultPrice,
                childPrice,
                infantPrice,
                fare.getCurrency(),
                seatsAvailable,
                refundable,
                minHours,
                refundPercent,
                refundDesc
        );
    }

    public BigDecimal computeAdultPrice(BigDecimal baseFare, BigDecimal taxFee) {
        BigDecimal base = baseFare == null ? BigDecimal.ZERO : baseFare;
        BigDecimal tax = taxFee == null ? BigDecimal.ZERO : taxFee;
        return base.add(tax).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal computePercentagePrice(BigDecimal adultPrice, int percent) {
        return adultPrice
                .multiply(BigDecimal.valueOf(percent))
                .divide(HUNDRED, 0, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ================= Helpers =================

    private String cabinKey(Long flightId, SeatClass seatClass) {
        return flightId + ":" + seatClass.name();
    }

    private FlightFare findOrThrow(Long id) {
        return flightFareRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Giá vé theo chuyến không tồn tại"));
    }

    private Flight resolveFlight(Long flightId) {
        return flightRepository.findById(flightId)
                .filter(f -> !Boolean.TRUE.equals(f.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Chuyến bay không tồn tại"));
    }

    private FareClass resolveFareClass(Long fareClassId) {
        FareClass fc = fareClassRepository.findById(fareClassId)
                .filter(f -> !Boolean.TRUE.equals(f.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Hạng vé không tồn tại"));
        if (!Boolean.TRUE.equals(fc.getIsActive())) {
            throw new BusinessException("Hạng vé đang ngừng hoạt động: " + fc.getFareClassCode());
        }
        return fc;
    }

    private void validateAirlineMatch(Flight flight, FareClass fareClass) {
        if (!flight.getAirline().getId().equals(fareClass.getAirline().getId())) {
            throw new BusinessException("Hạng vé " + fareClass.getFareClassCode()
                    + " không thuộc hãng khai thác chuyến bay (" + flight.getAirline().getAirlineCode() + ")");
        }
    }

    private void validateEffectiveRange(LocalDateTime effectiveFrom, LocalDateTime effectiveTo) {
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new BusinessException("Thời điểm kết thúc hiệu lực (effective_to) phải sau thời điểm bắt đầu (effective_from)");
        }
    }

    private void validateNoOverlap(
            Long flightId,
            Long fareClassId,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveTo,
            Long excludeId) {
        if (flightFareRepository.existsOverlappingFare(flightId, fareClassId, effectiveFrom, effectiveTo, excludeId)) {
            log.warn("Trùng khoảng hiệu lực giá vé: flightId={}, fareClassId={}, from={}, to={}",
                    flightId, fareClassId, effectiveFrom, effectiveTo);
            throw new BusinessException(
                    "Khoảng thời gian hiệu lực bị chồng lấn với một dòng giá khác của cùng chuyến bay và hạng vé");
        }
    }

    private void applyMutableFields(
            FlightFare fare,
            FlightFareRequest request,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveTo) {
        fare.setBaseFare(request.baseFare().setScale(2, RoundingMode.HALF_UP));
        fare.setTaxFee(request.taxFee().setScale(2, RoundingMode.HALF_UP));
        fare.setCurrency(request.currency() == null || request.currency().isBlank()
                ? "VND"
                : request.currency().trim().toUpperCase());
        fare.setEffectiveFrom(effectiveFrom);
        fare.setEffectiveTo(effectiveTo);
    }

    private FlightFareResponse toResponse(FlightFare fare) {
        BigDecimal adultPrice = computeAdultPrice(fare.getBaseFare(), fare.getTaxFee());
        BigDecimal childPrice = computePercentagePrice(adultPrice, pricingProperties.getChildFarePercent());
        BigDecimal infantPrice = computePercentagePrice(adultPrice, pricingProperties.getInfantFarePercent());
        return flightFareMapper.toResponse(fare, adultPrice, childPrice, infantPrice);
    }

    private Specification<FlightFare> buildSpec(ListQuery query, Long flightId, Long fareClassId) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("flight").get("isDeleted")));

            if (flightId != null) {
                predicates.add(cb.equal(root.get("flight").get("id"), flightId));
            }
            if (fareClassId != null) {
                predicates.add(cb.equal(root.get("fareClass").get("id"), fareClassId));
            }

            String keyword = query.getKeywordOrNull();
            if (keyword != null) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("flight").get("flightNumber")), like),
                        cb.like(cb.lower(root.get("fareClass").get("fareClassCode")), like),
                        cb.like(cb.lower(root.get("fareClass").get("fareClassName")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

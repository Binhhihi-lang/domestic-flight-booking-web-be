package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.dto.request.promotion.PromotionRequest;
import com.example.domesticflightbookingwebbackend.dto.response.promotion.PromotionResponse;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import com.example.domesticflightbookingwebbackend.entity.Airport;
import com.example.domesticflightbookingwebbackend.entity.DiscountType;
import com.example.domesticflightbookingwebbackend.entity.FareClass;
import com.example.domesticflightbookingwebbackend.entity.Flight;
import com.example.domesticflightbookingwebbackend.entity.Promotion;
import com.example.domesticflightbookingwebbackend.entity.PromotionExcludedFlight;
import com.example.domesticflightbookingwebbackend.entity.PromotionScope;
import com.example.domesticflightbookingwebbackend.entity.ScopeTargetType;
import com.example.domesticflightbookingwebbackend.entity.TripTypeCondition;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.DuplicateResourceException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.repository.AirlineRepository;
import com.example.domesticflightbookingwebbackend.repository.AirportRepository;
import com.example.domesticflightbookingwebbackend.repository.FareClassRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightRepository;
import com.example.domesticflightbookingwebbackend.repository.PromotionExcludedFlightRepository;
import com.example.domesticflightbookingwebbackend.repository.PromotionRepository;
import com.example.domesticflightbookingwebbackend.repository.PromotionScopeRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Promotion — quản lý định nghĩa (mục 2.7 API v0.2). Tính giá thuộc Phase 2–3. */
@Slf4j
@Service
public class PromotionService extends BaseCrudService<Promotion, Long, PromotionRequest, PromotionResponse> {

    private static final String[] SORT_WHITELIST = {"start_date", "end_date", "promotion_code"};

    private final PromotionRepository promotionRepository;
    private final PromotionScopeRepository scopeRepository;
    private final PromotionExcludedFlightRepository excludedRepository;
    private final AirlineRepository airlineRepository;
    private final FareClassRepository fareClassRepository;
    private final FlightRepository flightRepository;
    private final AirportRepository airportRepository;

    public PromotionService(PromotionRepository repository,
                            PromotionScopeRepository scopeRepository,
                            PromotionExcludedFlightRepository excludedRepository,
                            AirlineRepository airlineRepository,
                            FareClassRepository fareClassRepository,
                            FlightRepository flightRepository,
                            AirportRepository airportRepository) {
        super(repository);
        this.promotionRepository = repository;
        this.scopeRepository = scopeRepository;
        this.excludedRepository = excludedRepository;
        this.airlineRepository = airlineRepository;
        this.fareClassRepository = fareClassRepository;
        this.flightRepository = flightRepository;
        this.airportRepository = airportRepository;
    }

    @Override
    protected String resourceName() {
        return "Khuyến mãi";
    }

    @Override
    protected PromotionResponse toResponse(Promotion entity) {
        return toResponseWithDetails(entity);
    }

    @Override
    protected Promotion createEntity(PromotionRequest request) {
        String code = request.promotionCode().trim().toUpperCase();
        if (promotionRepository.existsByPromotionCodeAndIsDeletedFalse(code)) {
            log.warn("Tạo khuyến mãi thất bại: mã {} đã tồn tại", code);
            throw new DuplicateResourceException("Mã khuyến mãi đã tồn tại: " + code);
        }
        Promotion promotion = new Promotion();
        promotion.setPromotionCode(code);
        applyRequest(promotion, request);
        return promotion;
    }

    @Override
    protected void updateEntity(Promotion entity, PromotionRequest request) {
        applyRequest(entity, request);   // promotion_code không đổi
    }

    private void applyRequest(Promotion promotion, PromotionRequest request) {
        validate(request);
        promotion.setPromotionName(request.promotionName().trim());
        promotion.setDescription(request.description());
        promotion.setImageUrl(request.imageUrl());
        promotion.setBadgeLabel(request.badgeLabel());
        promotion.setDiscountType(DiscountType.valueOf(request.discountType()));
        promotion.setDiscountValue(request.discountValue());
        promotion.setMaxDiscountAmount(request.maxDiscountAmount());
        promotion.setMinFareAmount(request.minFareAmount());
        promotion.setApplicableTripType(request.applicableTripType() == null
                ? TripTypeCondition.ANY
                : TripTypeCondition.valueOf(request.applicableTripType()));
        promotion.setStartDate(request.startDate());
        promotion.setEndDate(request.endDate());
        promotion.setIsActive(request.isActive() == null || request.isActive());
    }

    /** Ràng buộc DB v0.4 → 1006/1001. */
    private void validate(PromotionRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessException("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu");
        }
        if (request.discountValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Giá trị giảm phải lớn hơn 0");
        }
        if ("PERCENT".equals(request.discountType())
                && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException("Giảm theo phần trăm không được vượt quá 100");
        }
        if (request.maxDiscountAmount() != null
                && request.maxDiscountAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Trần giảm phải lớn hơn 0");
        }
        if (request.minFareAmount() != null
                && request.minFareAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Giá vé tối thiểu không được âm");
        }
    }

    // ================= Scopes / Excluded =================

    /** PUT thay TOÀN BỘ scopes + excluded flights. */
    @Override
    @Transactional
    public PromotionResponse update(Long id, PromotionRequest request) {
        Promotion promotion = findOrThrow(id);
        updateEntity(promotion, request);
        promotionRepository.save(promotion);
        replaceScopes(promotion, request.scopes());
        replaceExcluded(promotion, request.excludedFlightIds());
        log.info("Cập nhật khuyến mãi id={} thành công", id);
        return toResponseWithDetails(promotion);
    }

    @Override
    @Transactional
    public PromotionResponse create(PromotionRequest request) {
        Promotion saved = promotionRepository.save(createEntity(request));
        replaceScopes(saved, request.scopes());
        replaceExcluded(saved, request.excludedFlightIds());
        log.info("Tạo khuyến mãi thành công: {}", saved.getPromotionCode());
        return toResponseWithDetails(saved);
    }

    private void replaceScopes(Promotion promotion, List<PromotionRequest.ScopeItem> items) {
        scopeRepository.deleteAll(scopeRepository.findByPromotionId(promotion.getId()));
        if (items == null) {
            return;
        }
        for (PromotionRequest.ScopeItem item : items) {
            scopeRepository.save(toScope(promotion, item));
        }
    }

    private PromotionScope toScope(Promotion promotion, PromotionRequest.ScopeItem item) {
        ScopeTargetType type = parseTargetType(item.targetType());
        PromotionScope scope = new PromotionScope();
        scope.setPromotion(promotion);
        scope.setTargetType(type);
        switch (type) {
            case AIRLINE -> scope.setAirline(requireAirline(item.airlineId()));
            case FARE_CLASS -> scope.setFareClass(requireFareClass(item.fareClassId()));
            case FLIGHT -> scope.setFlight(requireFlight(item.flightId()));
            case ROUTE -> {
                // ROUTE: bắt buộc có ít nhất sân bay đi
                if (item.departureAirportId() == null) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR,
                            "Phạm vi ROUTE phải có departure_airport_id");
                }
                scope.setDepartureAirport(requireAirport(item.departureAirportId()));
                if (item.arrivalAirportId() != null) {
                    scope.setArrivalAirport(requireAirport(item.arrivalAirportId()));
                }
            }
        }
        return scope;
    }

    private ScopeTargetType parseTargetType(String raw) {
        try {
            return ScopeTargetType.valueOf(raw == null ? "" : raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "target_type không hợp lệ: " + raw);
        }
    }

    private Airline requireAirline(Long id) {
        if (id == null) throw new AppException(ErrorCode.VALIDATION_ERROR, "Thiếu airline_id");
        return airlineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hãng bay không tồn tại: " + id));
    }

    private FareClass requireFareClass(Long id) {
        if (id == null) throw new AppException(ErrorCode.VALIDATION_ERROR, "Thiếu fare_class_id");
        return fareClassRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hạng vé không tồn tại: " + id));
    }

    private Flight requireFlight(Long id) {
        if (id == null) throw new AppException(ErrorCode.VALIDATION_ERROR, "Thiếu flight_id");
        return flightRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chuyến bay không tồn tại: " + id));
    }

    private Airport requireAirport(Long id) {
        return airportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sân bay không tồn tại: " + id));
    }

    private void replaceExcluded(Promotion promotion, List<Long> flightIds) {
        excludedRepository.deleteAll(excludedRepository.findByPromotionId(promotion.getId()));
        if (flightIds == null) {
            return;
        }
        for (Long flightId : flightIds) {
            PromotionExcludedFlight ex = new PromotionExcludedFlight();
            ex.setPromotion(promotion);
            ex.setFlight(requireFlight(flightId));
            excludedRepository.save(ex);
        }
    }

    // ================= Response =================

    private PromotionResponse toResponseWithDetails(Promotion promotion) {
        List<PromotionResponse.ScopeResponse> scopes = scopeRepository.findByPromotionId(promotion.getId())
                .stream()
                .map(s -> new PromotionResponse.ScopeResponse(
                        s.getTargetType().name(),
                        s.getAirline() == null ? null : s.getAirline().getId(),
                        s.getFareClass() == null ? null : s.getFareClass().getId(),
                        s.getFlight() == null ? null : s.getFlight().getId(),
                        s.getDepartureAirport() == null ? null : s.getDepartureAirport().getId(),
                        s.getArrivalAirport() == null ? null : s.getArrivalAirport().getId()))
                .toList();
        List<Long> excluded = excludedRepository.findByPromotionId(promotion.getId())
                .stream().map(e -> e.getFlight().getId()).toList();

        return new PromotionResponse(
                promotion.getId(), promotion.getPromotionCode(), promotion.getPromotionName(),
                promotion.getDescription(), promotion.getImageUrl(), promotion.getBadgeLabel(),
                promotion.getDiscountType().name(), promotion.getDiscountValue(),
                promotion.getMaxDiscountAmount(), promotion.getMinFareAmount(),
                promotion.getApplicableTripType().name(),
                promotion.getStartDate(), promotion.getEndDate(),
                Boolean.TRUE.equals(promotion.getIsActive()) ? "ACTIVE" : "INACTIVE",
                scopes, excluded);
    }

    @Override
    protected void softDelete(Promotion entity) {
        entity.setIsDeleted(true);
        entity.setIsActive(false);
        entity.setDeletedAt(LocalDateTime.now());
    }

    @Override
    protected void setActive(Promotion entity, boolean active) {
        entity.setIsActive(active);
    }

    @Override
    protected Page<Promotion> search(ListQuery query, Map<String, String> filters) {
        Pageable pageable = PageMapper.toPageable(query, "startDate", SORT_WHITELIST);
        return promotionRepository.findAll(buildSpec(query, filters), pageable);
    }

    /**
     * Lọc: khách chỉ thấy promotion đang hiệu lực (is_active + trong khoảng ngày);
     * ADMIN/STAFF truyền is_active để xem cả bản tắt.
     */
    private Specification<Promotion> buildSpec(ListQuery query, Map<String, String> filters) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));   // BR-LIST-04

            String isActive = filters.get("is_active");
            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), Boolean.parseBoolean(isActive)));
            } else {
                // Mặc định (khách): chỉ promotion đang hiệu lực
                LocalDate today = LocalDate.now();
                predicates.add(cb.isTrue(root.get("isActive")));
                predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), today));
                predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), today));
            }

            String discountType = filters.get("discount_type");
            if (discountType != null) {
                predicates.add(cb.equal(root.get("discountType"), DiscountType.valueOf(discountType)));
            }
            String tripType = filters.get("applicable_trip_type");
            if (tripType != null) {
                predicates.add(cb.equal(root.get("applicableTripType"),
                        TripTypeCondition.valueOf(tripType)));
            }

            String keyword = query.getKeywordOrNull();
            if (keyword != null) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("promotionCode")), like),
                        cb.like(cb.lower(root.get("promotionName")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

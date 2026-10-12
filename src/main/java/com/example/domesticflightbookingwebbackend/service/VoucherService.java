package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.dto.request.voucher.VoucherRequest;
import com.example.domesticflightbookingwebbackend.dto.response.voucher.MyVoucherResponse;
import com.example.domesticflightbookingwebbackend.dto.response.voucher.VoucherResponse;
import com.example.domesticflightbookingwebbackend.dto.response.voucher.VoucherStoreItem;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import com.example.domesticflightbookingwebbackend.entity.Airport;
import com.example.domesticflightbookingwebbackend.entity.DiscountType;
import com.example.domesticflightbookingwebbackend.entity.FareClass;
import com.example.domesticflightbookingwebbackend.entity.Flight;
import com.example.domesticflightbookingwebbackend.entity.ScopeTargetType;
import com.example.domesticflightbookingwebbackend.entity.TripTypeCondition;
import com.example.domesticflightbookingwebbackend.entity.User;
import com.example.domesticflightbookingwebbackend.entity.UserVoucher;
import com.example.domesticflightbookingwebbackend.entity.UserVoucherSource;
import com.example.domesticflightbookingwebbackend.entity.UserVoucherStatus;
import com.example.domesticflightbookingwebbackend.entity.Voucher;
import com.example.domesticflightbookingwebbackend.entity.VoucherDistributionType;
import com.example.domesticflightbookingwebbackend.entity.VoucherExcludedFlight;
import com.example.domesticflightbookingwebbackend.entity.VoucherScope;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.DuplicateResourceException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.repository.AirlineRepository;
import com.example.domesticflightbookingwebbackend.repository.AirportRepository;
import com.example.domesticflightbookingwebbackend.repository.FareClassRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightRepository;
import com.example.domesticflightbookingwebbackend.repository.UserRepository;
import com.example.domesticflightbookingwebbackend.repository.UserVoucherRepository;
import com.example.domesticflightbookingwebbackend.repository.VoucherExcludedFlightRepository;
import com.example.domesticflightbookingwebbackend.repository.VoucherRepository;
import com.example.domesticflightbookingwebbackend.repository.VoucherScopeRepository;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Voucher — định nghĩa + Claim + ví khách (mục 2.8 API v0.2). */
@Slf4j
@Service
public class VoucherService extends BaseCrudService<Voucher, Long, VoucherRequest, VoucherResponse> {

    private static final String[] SORT_WHITELIST = {"start_date", "end_date", "voucher_code"};

    private final VoucherRepository voucherRepository;
    private final VoucherScopeRepository scopeRepository;
    private final VoucherExcludedFlightRepository excludedRepository;
    private final UserVoucherRepository userVoucherRepository;
    private final UserRepository userRepository;
    private final AirlineRepository airlineRepository;
    private final FareClassRepository fareClassRepository;
    private final FlightRepository flightRepository;
    private final AirportRepository airportRepository;

    public VoucherService(VoucherRepository repository,
                          VoucherScopeRepository scopeRepository,
                          VoucherExcludedFlightRepository excludedRepository,
                          UserVoucherRepository userVoucherRepository,
                          UserRepository userRepository,
                          AirlineRepository airlineRepository,
                          FareClassRepository fareClassRepository,
                          FlightRepository flightRepository,
                          AirportRepository airportRepository) {
        super(repository);
        this.voucherRepository = repository;
        this.scopeRepository = scopeRepository;
        this.excludedRepository = excludedRepository;
        this.userVoucherRepository = userVoucherRepository;
        this.userRepository = userRepository;
        this.airlineRepository = airlineRepository;
        this.fareClassRepository = fareClassRepository;
        this.flightRepository = flightRepository;
        this.airportRepository = airportRepository;
    }

    @Override
    protected String resourceName() {
        return "Voucher";
    }

    @Override
    protected VoucherResponse toResponse(Voucher entity) {
        return toResponseWithDetails(entity);
    }

    @Override
    protected Voucher createEntity(VoucherRequest request) {
        String code = request.voucherCode().trim().toUpperCase();
        if (voucherRepository.existsByVoucherCodeAndIsDeletedFalse(code)) {
            log.warn("Tạo voucher thất bại: mã {} đã tồn tại", code);
            throw new DuplicateResourceException("Mã voucher đã tồn tại: " + code);
        }
        Voucher voucher = new Voucher();
        voucher.setVoucherCode(code);
        applyRequest(voucher, request);
        return voucher;
    }

    @Override
    protected void updateEntity(Voucher entity, VoucherRequest request) {
        applyRequest(entity, request);   // voucher_code không đổi
    }

    private void applyRequest(Voucher voucher, VoucherRequest request) {
        validate(request);
        voucher.setVoucherName(request.voucherName().trim());
        voucher.setDescription(request.description());
        voucher.setDistributionType(VoucherDistributionType.valueOf(request.distributionType()));
        voucher.setDiscountType(DiscountType.valueOf(request.discountType()));
        voucher.setDiscountValue(request.discountValue());
        voucher.setMaxDiscountAmount(request.maxDiscountAmount());
        voucher.setMinOrderAmount(request.minOrderAmount());
        voucher.setApplicableTripType(request.applicableTripType() == null
                ? TripTypeCondition.ANY
                : TripTypeCondition.valueOf(request.applicableTripType()));
        voucher.setStartDate(request.startDate());
        voucher.setEndDate(request.endDate());
        voucher.setClaimLimit(request.claimLimit());
        voucher.setIsActive(request.isActive() == null || request.isActive());
    }

    private void validate(VoucherRequest request) {
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
        if (request.minOrderAmount() != null
                && request.minOrderAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Giá trị đơn tối thiểu không được âm");
        }
        if (request.claimLimit() != null && request.claimLimit() <= 0) {
            throw new BusinessException("Giới hạn lượt nhận phải lớn hơn 0");
        }
    }

    // ================= Create / Update (kèm scopes) =================

    @Override
    @Transactional
    public VoucherResponse create(VoucherRequest request) {
        Voucher saved = voucherRepository.save(createEntity(request));
        replaceScopes(saved, request.scopes());
        replaceExcluded(saved, request.excludedFlightIds());
        log.info("Tạo voucher thành công: {}", saved.getVoucherCode());
        return toResponseWithDetails(saved);
    }

    @Override
    @Transactional
    public VoucherResponse update(Long id, VoucherRequest request) {
        Voucher voucher = findOrThrow(id);
        updateEntity(voucher, request);
        voucherRepository.save(voucher);
        replaceScopes(voucher, request.scopes());
        replaceExcluded(voucher, request.excludedFlightIds());
        log.info("Cập nhật voucher id={} thành công", id);
        return toResponseWithDetails(voucher);
    }

    private void replaceScopes(Voucher voucher, List<VoucherRequest.PromotionScopeItem> items) {
        scopeRepository.deleteAll(scopeRepository.findByVoucherId(voucher.getId()));
        if (items == null) {
            return;
        }
        for (VoucherRequest.PromotionScopeItem item : items) {
            scopeRepository.save(toScope(voucher, item));
        }
    }

    private VoucherScope toScope(Voucher voucher, VoucherRequest.PromotionScopeItem item) {
        ScopeTargetType type = parseTargetType(item.targetType());
        VoucherScope scope = new VoucherScope();
        scope.setVoucher(voucher);
        scope.setTargetType(type);
        switch (type) {
            case AIRLINE -> scope.setAirline(requireAirline(item.airlineId()));
            case FARE_CLASS -> scope.setFareClass(requireFareClass(item.fareClassId()));
            case FLIGHT -> scope.setFlight(requireFlight(item.flightId()));
            case ROUTE -> {
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

    private void replaceExcluded(Voucher voucher, List<Long> flightIds) {
        excludedRepository.deleteAll(excludedRepository.findByVoucherId(voucher.getId()));
        if (flightIds == null) {
            return;
        }
        for (Long flightId : flightIds) {
            VoucherExcludedFlight ex = new VoucherExcludedFlight();
            ex.setVoucher(voucher);
            ex.setFlight(requireFlight(flightId));
            excludedRepository.save(ex);
        }
    }

    // ================= Claim (CUSTOMER) =================

    /** Nhận voucher GLOBAL. Hết lượt → 1006; đã nhận → 1005. */
    @Transactional
    public UserVoucher claim(Long voucherId, Long userId) {
        Voucher voucher = findOrThrow(voucherId);
        if (voucher.getDistributionType() != VoucherDistributionType.GLOBAL) {
            throw new BusinessException("Chỉ nhận được voucher phát toàn hệ thống (GLOBAL)");
        }
        if (!isCurrentlyValid(voucher)) {
            throw new BusinessException("Voucher chưa tới thời gian nhận hoặc đã hết hạn");
        }
        if (userVoucherRepository.existsByUserIdAndVoucherId(userId, voucherId)) {
            throw new DuplicateResourceException("Bạn đã nhận voucher này rồi");
        }

        // UPDATE có điều kiện: 1 dòng bị ảnh hưởng mới thực sự còn lượt
        int updated = voucherRepository.incrementClaimedCount(voucherId);
        if (updated == 0) {
            log.warn("Nhận voucher id={} thất bại: đã hết lượt", voucherId);
            throw new BusinessException("Voucher đã hết lượt nhận");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        UserVoucher uv = new UserVoucher();
        uv.setUser(user);
        uv.setVoucher(voucher);
        uv.setStatus(UserVoucherStatus.AVAILABLE);
        uv.setSource(UserVoucherSource.CLAIMED);
        uv.setReceivedAt(LocalDateTime.now());
        uv.setExpiresAt(voucher.getEndDate().atTime(LocalTime.MAX));
        UserVoucher saved = userVoucherRepository.save(uv);
        log.info("User id={} nhận voucher id={} thành công", userId, voucherId);
        return saved;
    }

    // ================= Assign (ADMIN tặng) =================

    /** Tặng voucher ASSIGNED cho danh sách user. */
    @Transactional
    public int assign(Long voucherId, List<Long> userIds) {
        Voucher voucher = findOrThrow(voucherId);
        if (voucher.getDistributionType() != VoucherDistributionType.ASSIGNED) {
            throw new BusinessException("Chỉ tặng được voucher phát riêng (ASSIGNED)");
        }
        if (userIds == null || userIds.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Danh sách user không được để trống");
        }

        int count = 0;
        for (Long userId : userIds) {
            if (userVoucherRepository.existsByUserIdAndVoucherId(userId, voucherId)) {
                log.warn("Bỏ qua user id={}: đã có voucher id={}", userId, voucherId);
                continue;   // UNIQUE (user_id, voucher_id)
            }
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại: " + userId));

            UserVoucher uv = new UserVoucher();
            uv.setUser(user);
            uv.setVoucher(voucher);
            uv.setStatus(UserVoucherStatus.AVAILABLE);
            uv.setSource(UserVoucherSource.GIFTED);
            uv.setReceivedAt(LocalDateTime.now());
            uv.setExpiresAt(voucher.getEndDate().atTime(LocalTime.MAX));
            userVoucherRepository.save(uv);
            count++;
        }
        log.info("Tặng voucher id={} cho {} user", voucherId, count);
        return count;
    }

    // ================= Kho voucher + Ví khách =================

    /** Kho voucher: GLOBAL đang hiệu lực, còn lượt, kèm cờ claimed của khách. */
    @Transactional(readOnly = true)
    public List<VoucherStoreItem> getVoucherStore(Long userId) {
        return voucherRepository
                .findByDistributionTypeAndIsActiveTrueAndIsDeletedFalse(VoucherDistributionType.GLOBAL)
                .stream()
                .filter(this::isCurrentlyValid)
                .filter(v -> v.getClaimLimit() == null || v.getClaimedCount() < v.getClaimLimit())
                .map(v -> new VoucherStoreItem(
                        v.getId(), v.getVoucherCode(), v.getVoucherName(), v.getDescription(),
                        v.getDiscountType().name(), v.getDiscountValue(), v.getMaxDiscountAmount(),
                        v.getMinOrderAmount(), v.getApplicableTripType().name(),
                        v.getStartDate(), v.getEndDate(), v.getClaimLimit(), v.getClaimedCount(),
                        userVoucherRepository.existsByUserIdAndVoucherId(userId, v.getId())))
                .toList();
    }

    /** Ví voucher của khách (endpoint SELF — không nhận userId từ client). */
    @Transactional(readOnly = true)
    public Page<MyVoucherResponse> getMyVouchers(Long userId, Pageable pageable) {
        return userVoucherRepository.findByUserId(userId, pageable).map(this::toMyVoucher);
    }

    @Transactional(readOnly = true)
    public MyVoucherResponse getMyVoucher(Long userVoucherId, Long userId) {
        UserVoucher uv = userVoucherRepository.findByIdAndUserId(userVoucherId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher không có trong ví của bạn"));
        return toMyVoucher(uv);
    }

    private MyVoucherResponse toMyVoucher(UserVoucher uv) {
        Voucher v = uv.getVoucher();
        return new MyVoucherResponse(
                uv.getId(), uv.getStatus().name(), uv.getSource().name(),
                uv.getReceivedAt(), uv.getExpiresAt(), uv.getUsedAt(),
                new MyVoucherResponse.VoucherBrief(
                        v.getId(), v.getVoucherCode(), v.getVoucherName(), v.getDescription(),
                        v.getDiscountType().name(), v.getDiscountValue(), v.getMinOrderAmount(),
                        v.getApplicableTripType().name(), v.getStartDate(), v.getEndDate()));
    }

    // ================= Helpers =================

    private boolean isCurrentlyValid(Voucher v) {
        LocalDate today = LocalDate.now();
        return Boolean.TRUE.equals(v.getIsActive())
                && !today.isBefore(v.getStartDate())
                && !today.isAfter(v.getEndDate());
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

    private VoucherResponse toResponseWithDetails(Voucher v) {
        List<VoucherResponse.ScopeResponse> scopes = scopeRepository.findByVoucherId(v.getId()).stream()
                .map(s -> new VoucherResponse.ScopeResponse(
                        s.getTargetType().name(),
                        s.getAirline() == null ? null : s.getAirline().getId(),
                        s.getFareClass() == null ? null : s.getFareClass().getId(),
                        s.getFlight() == null ? null : s.getFlight().getId(),
                        s.getDepartureAirport() == null ? null : s.getDepartureAirport().getId(),
                        s.getArrivalAirport() == null ? null : s.getArrivalAirport().getId()))
                .toList();
        List<Long> excluded = excludedRepository.findByVoucherId(v.getId())
                .stream().map(e -> e.getFlight().getId()).toList();

        return new VoucherResponse(
                v.getId(), v.getVoucherCode(), v.getVoucherName(), v.getDescription(),
                v.getDistributionType().name(), v.getDiscountType().name(), v.getDiscountValue(),
                v.getMaxDiscountAmount(), v.getMinOrderAmount(), v.getApplicableTripType().name(),
                v.getStartDate(), v.getEndDate(), v.getClaimLimit(), v.getClaimedCount(),
                Boolean.TRUE.equals(v.getIsActive()) ? "ACTIVE" : "INACTIVE",
                scopes, excluded);
    }

    @Override
    protected void softDelete(Voucher entity) {
        entity.setIsDeleted(true);
        entity.setIsActive(false);
        entity.setDeletedAt(LocalDateTime.now());
    }

    @Override
    protected void setActive(Voucher entity, boolean active) {
        entity.setIsActive(active);
    }

    @Override
    protected Page<Voucher> search(ListQuery query, Map<String, String> filters) {
        Pageable pageable = PageMapper.toPageable(query, "startDate", SORT_WHITELIST);
        return voucherRepository.findAll(buildSpec(query, filters), pageable);
    }

    private Specification<Voucher> buildSpec(ListQuery query, Map<String, String> filters) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));   // BR-LIST-04

            String isActive = filters.get("is_active");
            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), Boolean.parseBoolean(isActive)));
            }
            String distributionType = filters.get("distribution_type");
            if (distributionType != null) {
                predicates.add(cb.equal(root.get("distributionType"),
                        VoucherDistributionType.valueOf(distributionType)));
            }
            String discountType = filters.get("discount_type");
            if (discountType != null) {
                predicates.add(cb.equal(root.get("discountType"), DiscountType.valueOf(discountType)));
            }

            String keyword = query.getKeywordOrNull();
            if (keyword != null) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("voucherCode")), like),
                        cb.like(cb.lower(root.get("voucherName")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

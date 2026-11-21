package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.dto.request.masterdata.FareClassRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.FareClassResponse;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import com.example.domesticflightbookingwebbackend.entity.FareClass;
import com.example.domesticflightbookingwebbackend.entity.SeatClass;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.DuplicateResourceException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.mapper.FareClassMapper;
import com.example.domesticflightbookingwebbackend.repository.AirlineRepository;
import com.example.domesticflightbookingwebbackend.repository.FareClassRepository;
import com.example.domesticflightbookingwebbackend.repository.FlightFareRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Hạng vé thuộc từng hãng (Phase 2). Mã hạng vé tự gắn tiền tố hãng (V18). */
@Slf4j
@Service
public class FareClassService extends BaseCrudService<FareClass, Long, FareClassRequest, FareClassResponse> {

    private static final String[] SORT_WHITELIST = {"fare_class_code", "fare_class_name", "seat_class"};
    private static final int MAX_CODE_LENGTH = 30;

    private final FareClassRepository fareClassRepository;
    private final AirlineRepository airlineRepository;
    private final FlightFareRepository flightFareRepository;
    private final FareClassMapper fareClassMapper;

    public FareClassService(
            FareClassRepository repository,
            AirlineRepository airlineRepository,
            FlightFareRepository flightFareRepository,
            FareClassMapper fareClassMapper) {
        super(repository);
        this.fareClassRepository = repository;
        this.airlineRepository = airlineRepository;
        this.flightFareRepository = flightFareRepository;
        this.fareClassMapper = fareClassMapper;
    }

    @Override
    protected String resourceName() {
        return "Hạng vé";
    }

    @Override
    protected FareClassResponse toResponse(FareClass entity) {
        return fareClassMapper.toResponse(entity);
    }

    @Override
    protected FareClass createEntity(FareClassRequest request) {
        Airline airline = resolveAirline(request.airlineId());
        String fullCode = buildPrefixedCode(airline, request.fareClassCode());
        if (fareClassRepository.existsByFareClassCodeAndIsDeletedFalse(fullCode)) {
            log.warn("Tạo hạng vé thất bại: mã {} đã tồn tại", fullCode);
            throw new DuplicateResourceException("Mã hạng vé đã tồn tại: " + fullCode);
        }

        FareClass fareClass = new FareClass();
        fareClass.setAirline(airline);
        fareClass.setFareClassCode(fullCode);
        applyMutableFields(fareClass, request);
        return fareClass;
    }

    @Override
    protected void updateEntity(FareClass entity, FareClassRequest request) {
        // airline và fare_class_code không đổi sau khi tạo để giữ toàn vẹn tham chiếu.
        applyMutableFields(entity, request);
    }

    private void applyMutableFields(FareClass fareClass, FareClassRequest request) {
        fareClass.setFareClassName(request.fareClassName().trim());
        fareClass.setSeatClass(SeatClass.valueOf(request.seatClass().toUpperCase()));
        fareClass.setIsActive(!"INACTIVE".equalsIgnoreCase(request.status()));
        fareClass.setUpdatedAt(LocalDateTime.now());
    }

    private Airline resolveAirline(Long airlineId) {
        return airlineRepository.findById(airlineId)
                .filter(a -> !Boolean.TRUE.equals(a.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Hãng hàng không không tồn tại"));
    }

    private String buildPrefixedCode(Airline airline, String rawCode) {
        String prefix = airline.getAirlineCode().trim().toUpperCase() + "_";
        String normalized = rawCode.trim().toUpperCase();
        String fullCode = normalized.startsWith(prefix) ? normalized : prefix + normalized;
        if (fullCode.length() > MAX_CODE_LENGTH) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Mã hạng vé sau khi gắn tiền tố hãng vượt quá " + MAX_CODE_LENGTH + " ký tự");
        }
        return fullCode;
    }

    /** Hạng vé đã được định giá chuyến bay → từ chối xóa mềm (chỉ cho tắt is_active). */
    @Override
    @Transactional
    public void delete(Long id) {
        findOrThrow(id);
        if (flightFareRepository.existsByFareClassId(id)) {
            log.warn("Xóa hạng vé id={} bị từ chối: đã có giá bán theo chuyến", id);
            throw new BusinessException(
                    "Hạng vé đang được sử dụng trong bảng giá chuyến bay — chỉ có thể tắt trạng thái");
        }
        super.delete(id);
    }

    @Override
    protected void softDelete(FareClass entity) {
        entity.setIsDeleted(true);
        entity.setIsActive(false);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
    }

    @Override
    protected void setActive(FareClass entity, boolean active) {
        entity.setIsActive(active);
        entity.setUpdatedAt(LocalDateTime.now());
    }

    @Override
    protected Page<FareClass> search(ListQuery query, Map<String, String> filters) {
        Pageable pageable = PageMapper.toPageable(query, "fareClassCode", SORT_WHITELIST);
        return fareClassRepository.findAll(buildSpec(query, filters), pageable);
    }

    private Specification<FareClass> buildSpec(ListQuery query, Map<String, String> filters) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));

            String airlineId = filters.get("airline_id");
            if (airlineId != null) {
                predicates.add(cb.equal(root.get("airline").get("id"), Long.valueOf(airlineId)));
            }

            String seatClass = filters.get("seat_class");
            if (seatClass != null) {
                predicates.add(cb.equal(root.get("seatClass"), SeatClass.valueOf(seatClass.toUpperCase())));
            }

            String status = filters.get("status");
            if (status != null) {
                predicates.add(cb.equal(root.get("isActive"), "ACTIVE".equalsIgnoreCase(status)));
            }

            String keyword = query.getKeywordOrNull();
            if (keyword != null) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fareClassCode")), like),
                        cb.like(cb.lower(root.get("fareClassName")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

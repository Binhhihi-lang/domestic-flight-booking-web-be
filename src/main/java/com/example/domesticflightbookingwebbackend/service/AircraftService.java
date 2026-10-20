package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.dto.request.masterdata.AircraftRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.AircraftResponse;
import com.example.domesticflightbookingwebbackend.entity.Aircraft;
import com.example.domesticflightbookingwebbackend.exception.DuplicateResourceException;
import com.example.domesticflightbookingwebbackend.mapper.AircraftMapper;
import com.example.domesticflightbookingwebbackend.repository.AircraftRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Aircraft — master data, KHÔNG public (chỉ STAFF/ADMIN), mục 2.6 API v0.2. */
@Slf4j
@Service
public class AircraftService extends BaseCrudService<Aircraft, Long, AircraftRequest, AircraftResponse> {

    private static final String[] SORT_WHITELIST = {"aircraft_code", "model"};

    private final AircraftRepository aircraftRepository;
    private final AircraftMapper aircraftMapper;

    public AircraftService(AircraftRepository repository, AircraftMapper aircraftMapper) {
        super(repository);
        this.aircraftRepository = repository;
        this.aircraftMapper = aircraftMapper;
    }

    @Override
    protected String resourceName() {
        return "Máy bay";
    }

    @Override
    protected AircraftResponse toResponse(Aircraft entity) {
        return aircraftMapper.toResponse(entity);
    }

    @Override
    protected Aircraft createEntity(AircraftRequest request) {
        String code = request.aircraftCode().trim().toUpperCase();
        if (aircraftRepository.existsByAircraftCodeAndIsDeletedFalse(code)) {
            log.warn("Tạo máy bay thất bại: mã {} đã tồn tại", code);
            throw new DuplicateResourceException("Mã máy bay đã tồn tại: " + code);
        }
        Aircraft aircraft = new Aircraft();
        aircraft.setAircraftCode(code);
        applyRequest(aircraft, request);
        return aircraft;
    }

    @Override
    protected void updateEntity(Aircraft entity, AircraftRequest request) {
        applyRequest(entity, request);   // aircraft_code không đổi
    }

    private void applyRequest(Aircraft aircraft, AircraftRequest request) {
        aircraft.setModel(request.model().trim());
        aircraft.setManufacturer(request.manufacturer());
        aircraft.setSeatCapacity(request.seatCapacity());
        aircraft.setAisleAfter(normalizeAisle(request.aisleAfter()));
        aircraft.setIsActive(!"INACTIVE".equalsIgnoreCase(request.status()));
    }

    /** Chuỗi trắng → null; chuẩn hoá chữ HOA và bỏ khoảng trắng quanh dấu phẩy. */
    private static String normalizeAisle(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.replaceAll("\\s+", "").toUpperCase();
    }

    @Override
    protected void softDelete(Aircraft entity) {
        entity.setIsDeleted(true);
        entity.setIsActive(false);
        entity.setDeletedAt(LocalDateTime.now());
    }

    @Override
    protected void setActive(Aircraft entity, boolean active) {
        entity.setIsActive(active);
    }

    @Override
    protected Page<Aircraft> search(ListQuery query, Map<String, String> filters) {
        Pageable pageable = PageMapper.toPageable(query, "aircraftCode", SORT_WHITELIST);
        return aircraftRepository.findAll(buildSpec(query), pageable);
    }

    private Specification<Aircraft> buildSpec(ListQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));   // BR-LIST-04

            String keyword = query.getKeywordOrNull();
            if (keyword != null) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("aircraftCode")), like),
                        cb.like(cb.lower(root.get("model")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

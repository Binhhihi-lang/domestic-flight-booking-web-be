package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.dto.request.masterdata.AirportRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.AirportResponse;
import com.example.domesticflightbookingwebbackend.entity.Airport;
import com.example.domesticflightbookingwebbackend.entity.City;
import com.example.domesticflightbookingwebbackend.exception.DuplicateResourceException;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.mapper.AirportMapper;
import com.example.domesticflightbookingwebbackend.repository.AirportRepository;
import com.example.domesticflightbookingwebbackend.repository.CityRepository;
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

/** Airport — master data (mục 2.5 API v0.2). Có FK city_id. */
@Slf4j
@Service
public class AirportService extends BaseCrudService<Airport, Long, AirportRequest, AirportResponse> {

    private static final String[] SORT_WHITELIST = {"airport_code", "airport_name"};

    private final AirportRepository airportRepository;
    private final CityRepository cityRepository;
    private final AirportMapper airportMapper;

    public AirportService(AirportRepository repository, CityRepository cityRepository,
                          AirportMapper airportMapper) {
        super(repository);
        this.airportRepository = repository;
        this.cityRepository = cityRepository;
        this.airportMapper = airportMapper;
    }

    @Override
    protected String resourceName() {
        return "Sân bay";
    }

    @Override
    protected AirportResponse toResponse(Airport entity) {
        return airportMapper.toResponse(entity);
    }

    @Override
    protected Airport createEntity(AirportRequest request) {
        String code = request.airportCode().trim().toUpperCase();
        if (airportRepository.existsByAirportCodeAndIsDeletedFalse(code)) {
            log.warn("Tạo sân bay thất bại: mã {} đã tồn tại", code);
            throw new DuplicateResourceException("Mã sân bay đã tồn tại: " + code);
        }
        Airport airport = new Airport();
        airport.setAirportCode(code);
        applyRequest(airport, request);
        return airport;
    }

    @Override
    protected void updateEntity(Airport entity, AirportRequest request) {
        applyRequest(entity, request);   // airport_code không đổi
    }

    private void applyRequest(Airport airport, AirportRequest request) {
        airport.setAirportName(request.airportName().trim());
        airport.setCity(resolveCity(request.cityId()));
        airport.setLatitude(request.latitude());
        airport.setLongitude(request.longitude());
        airport.setIsActive(!"INACTIVE".equalsIgnoreCase(request.status()));
    }

    /** city_id không tồn tại hoặc đã xóa mềm → 1004. */
    private City resolveCity(Long cityId) {
        return cityRepository.findById(cityId)
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Thành phố không tồn tại"));
    }

    @Override
    protected void softDelete(Airport entity) {
        entity.setIsDeleted(true);
        entity.setIsActive(false);
        entity.setDeletedAt(LocalDateTime.now());
    }

    @Override
    protected void setActive(Airport entity, boolean active) {
        entity.setIsActive(active);
    }

    @Override
    protected Page<Airport> search(ListQuery query, Map<String, String> filters) {
        Pageable pageable = PageMapper.toPageable(query, "airportCode", SORT_WHITELIST);
        return airportRepository.findAll(buildSpec(query, filters), pageable);
    }

    /** Vùng khách: chỉ trả sân bay đang hoạt động (mục 2.11 API v0.4). */
    @Transactional(readOnly = true)
    public PageData<AirportResponse> listPublic(ListQuery query, String cityId) {
        Pageable pageable = PageMapper.toPageable(query, "airportCode", SORT_WHITELIST);
        Map<String, String> filters = cityId == null || cityId.isBlank()
                ? Map.of() : Map.of("city_id", cityId);
        Specification<Airport> spec = buildSpec(query, filters)
                .and((root, cq, cb) -> cb.isTrue(root.get("isActive")));
        return PageMapper.toPageData(airportRepository.findAll(spec, pageable), this::toResponse);
    }

    /** Chi tiết cho khách: sân bay đã tắt/xóa coi như không tồn tại. */
    @Transactional(readOnly = true)
    public AirportResponse getPublicById(Long id) {
        Airport airport = findOrThrow(id);
        if (!Boolean.TRUE.equals(airport.getIsActive())) {
            throw new ResourceNotFoundException(resourceName() + " không tồn tại");
        }
        return toResponse(airport);
    }

    private Specification<Airport> buildSpec(ListQuery query, Map<String, String> filters) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));   // BR-LIST-04

            String cityId = filters.get("city_id");
            if (cityId != null) {
                predicates.add(cb.equal(root.get("city").get("id"), Long.valueOf(cityId)));
            }

            String keyword = query.getKeywordOrNull();
            if (keyword != null) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("airportCode")), like),
                        cb.like(cb.lower(root.get("airportName")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

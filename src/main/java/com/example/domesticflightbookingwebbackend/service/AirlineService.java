package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.dto.request.masterdata.AirlineRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.AirlineResponse;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import com.example.domesticflightbookingwebbackend.exception.DuplicateResourceException;
import com.example.domesticflightbookingwebbackend.mapper.AirlineMapper;
import com.example.domesticflightbookingwebbackend.repository.AirlineRepository;
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

/** Airline — module MẪU cho master data (mục 2.5 API v0.2). */
@Slf4j
@Service
public class AirlineService extends BaseCrudService<Airline, Long, AirlineRequest, AirlineResponse> {

    private static final String[] SORT_WHITELIST = {"airline_code", "airline_name"};

    private final AirlineMapper airlineMapper;
    private final AirlineRepository airlineRepository;

    public AirlineService(AirlineRepository repository, AirlineMapper airlineMapper) {
        super(repository);
        this.airlineRepository = repository;
        this.airlineMapper = airlineMapper;
    }

    @Override
    protected String resourceName() {
        return "Hãng hàng không";
    }

    @Override
    protected AirlineResponse toResponse(Airline entity) {
        return airlineMapper.toResponse(entity);
    }

    @Override
    protected Airline createEntity(AirlineRequest request) {
        String code = request.airlineCode().trim().toUpperCase();
        if (airlineRepository.existsByAirlineCodeAndIsDeletedFalse(code)) {
            log.warn("Tạo hãng thất bại: mã {} đã tồn tại", code);
            throw new DuplicateResourceException("Mã hãng đã tồn tại: " + code);
        }
        Airline airline = new Airline();
        airline.setAirlineCode(code);
        applyRequest(airline, request);
        return airline;
    }

    @Override
    protected void updateEntity(Airline entity, AirlineRequest request) {
        applyRequest(entity, request);   // airline_code không đổi
    }

    private void applyRequest(Airline airline, AirlineRequest request) {
        airline.setAirlineName(request.airlineName().trim());
        airline.setCountry(request.country());
        airline.setLogoUrl(request.logoUrl());
        airline.setIsActive(!"INACTIVE".equalsIgnoreCase(request.status()));
    }

    @Override
    protected void softDelete(Airline entity) {
        entity.setIsDeleted(true);
        entity.setIsActive(false);
        entity.setDeletedAt(LocalDateTime.now());
    }

    @Override
    protected void setActive(Airline entity, boolean active) {
        entity.setIsActive(active);
    }

    @Override
    protected Page<Airline> search(ListQuery query, Map<String, String> filters) {
        Pageable pageable = PageMapper.toPageable(query, "airlineCode", SORT_WHITELIST);
        return airlineRepository.findAll(buildSpec(query, filters), pageable);
    }

    private Specification<Airline> buildSpec(ListQuery query, Map<String, String> filters) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));   // BR-LIST-04

            String status = filters.get("status");
            if (status != null) {
                predicates.add(cb.equal(root.get("isActive"), "ACTIVE".equalsIgnoreCase(status)));
            }
            String country = filters.get("country");
            if (country != null) {
                predicates.add(cb.equal(cb.lower(root.get("country")), country.toLowerCase()));
            }

            String keyword = query.getKeywordOrNull();
            if (keyword != null) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("airlineCode")), like),
                        cb.like(cb.lower(root.get("airlineName")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

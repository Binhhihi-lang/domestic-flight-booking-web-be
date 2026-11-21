package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.dto.request.masterdata.BaggageOptionRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.BaggageOptionResponse;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import com.example.domesticflightbookingwebbackend.entity.BaggageOption;
import com.example.domesticflightbookingwebbackend.exception.DuplicateResourceException;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.mapper.BaggageOptionMapper;
import com.example.domesticflightbookingwebbackend.repository.AirlineRepository;
import com.example.domesticflightbookingwebbackend.repository.BaggageOptionRepository;
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

/** Gói hành lý ký gửi thuộc từng hãng (Phase 2). */
@Slf4j
@Service
public class BaggageOptionService extends BaseCrudService<BaggageOption, Long, BaggageOptionRequest, BaggageOptionResponse> {

    private static final String[] SORT_WHITELIST = {"name", "weight_kg", "price"};

    private final BaggageOptionRepository baggageOptionRepository;
    private final AirlineRepository airlineRepository;
    private final BaggageOptionMapper baggageOptionMapper;

    public BaggageOptionService(
            BaggageOptionRepository repository,
            AirlineRepository airlineRepository,
            BaggageOptionMapper baggageOptionMapper) {
        super(repository);
        this.baggageOptionRepository = repository;
        this.airlineRepository = airlineRepository;
        this.baggageOptionMapper = baggageOptionMapper;
    }

    @Override
    protected String resourceName() {
        return "Gói hành lý";
    }

    @Override
    protected BaggageOptionResponse toResponse(BaggageOption entity) {
        return baggageOptionMapper.toResponse(entity);
    }

    @Override
    protected BaggageOption createEntity(BaggageOptionRequest request) {
        Airline airline = resolveAirline(request.airlineId());
        String name = request.name().trim();
        if (baggageOptionRepository.existsByAirlineIdAndNameIgnoreCaseAndIsDeletedFalse(airline.getId(), name)) {
            log.warn("Tạo gói hành lý thất bại: hãng id={} đã có gói '{}'", airline.getId(), name);
            throw new DuplicateResourceException("Tên gói hành lý đã tồn tại cho hãng này: " + name);
        }

        BaggageOption option = new BaggageOption();
        option.setAirline(airline);
        applyMutableFields(option, request, name);
        return option;
    }

    @Override
    protected void updateEntity(BaggageOption entity, BaggageOptionRequest request) {
        String name = request.name().trim();
        Long airlineId = entity.getAirline().getId();
        if (baggageOptionRepository.existsByAirlineIdAndNameIgnoreCaseAndIsDeletedFalseAndIdNot(
                airlineId, name, entity.getId())) {
            log.warn("Cập nhật gói hành lý id={} thất bại: trùng tên '{}'", entity.getId(), name);
            throw new DuplicateResourceException("Tên gói hành lý đã tồn tại cho hãng này: " + name);
        }
        applyMutableFields(entity, request, name);
    }

    private void applyMutableFields(BaggageOption option, BaggageOptionRequest request, String name) {
        option.setName(name);
        option.setWeightKg(request.weightKg());
        option.setPrice(request.price());
        option.setIsActive(!"INACTIVE".equalsIgnoreCase(request.status()));
        option.setUpdatedAt(LocalDateTime.now());
    }

    private Airline resolveAirline(Long airlineId) {
        return airlineRepository.findById(airlineId)
                .filter(a -> !Boolean.TRUE.equals(a.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Hãng hàng không không tồn tại"));
    }

    @Override
    protected void softDelete(BaggageOption entity) {
        entity.setIsDeleted(true);
        entity.setIsActive(false);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
    }

    @Override
    protected void setActive(BaggageOption entity, boolean active) {
        entity.setIsActive(active);
        entity.setUpdatedAt(LocalDateTime.now());
    }

    @Override
    protected Page<BaggageOption> search(ListQuery query, Map<String, String> filters) {
        Pageable pageable = PageMapper.toPageable(query, "weightKg", SORT_WHITELIST);
        return baggageOptionRepository.findAll(buildSpec(query, filters), pageable);
    }

    private Specification<BaggageOption> buildSpec(ListQuery query, Map<String, String> filters) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));

            String airlineId = filters.get("airline_id");
            if (airlineId != null) {
                predicates.add(cb.equal(root.get("airline").get("id"), Long.valueOf(airlineId)));
            }

            String status = filters.get("status");
            if (status != null) {
                predicates.add(cb.equal(root.get("isActive"), "ACTIVE".equalsIgnoreCase(status)));
            }

            String keyword = query.getKeywordOrNull();
            if (keyword != null) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), like));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

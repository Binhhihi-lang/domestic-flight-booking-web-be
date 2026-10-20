package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.dto.request.masterdata.SeatClassSurchargeRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.SeatClassSurchargeResponse;
import com.example.domesticflightbookingwebbackend.entity.SeatClass;
import com.example.domesticflightbookingwebbackend.entity.SeatClassSurcharge;
import com.example.domesticflightbookingwebbackend.exception.DuplicateResourceException;
import com.example.domesticflightbookingwebbackend.mapper.SeatClassSurchargeMapper;
import com.example.domesticflightbookingwebbackend.repository.SeatClassSurchargeRepository;
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

/**
 * Phụ thu nâng hạng ghế — mặc định dùng chung mọi chuyến.
 *
 * <p><b>Lưu ý cho Phase 2:</b> không tìm thấy cặp (from, to) nghĩa là CHƯA CÓ
 * CHÍNH SÁCH → phải từ chối bán ghế đó. KHÔNG được lùi về 0 — làm vậy thì ADMIN
 * xóa nhầm một dòng sẽ khiến khách bay hạng cao miễn phí mà không có cảnh báo.
 */
@Slf4j
@Service
public class SeatClassSurchargeService
        extends BaseCrudService<SeatClassSurcharge, Long,
                SeatClassSurchargeRequest, SeatClassSurchargeResponse> {

    private static final String[] SORT_WHITELIST = {"fromClass", "toClass", "surcharge"};

    private final SeatClassSurchargeRepository seatClassSurchargeRepository;
    private final SeatClassSurchargeMapper seatClassSurchargeMapper;

    public SeatClassSurchargeService(SeatClassSurchargeRepository repository,
                                     SeatClassSurchargeMapper mapper) {
        super(repository);
        this.seatClassSurchargeRepository = repository;
        this.seatClassSurchargeMapper = mapper;
    }

    @Override
    protected String resourceName() {
        return "Phụ thu nâng hạng";
    }

    @Override
    protected SeatClassSurchargeResponse toResponse(SeatClassSurcharge entity) {
        return seatClassSurchargeMapper.toResponse(entity);
    }

    @Override
    protected SeatClassSurcharge createEntity(SeatClassSurchargeRequest request) {
        SeatClass from = parseClass(request.fromClass());
        SeatClass to = parseClass(request.toClass());
        if (seatClassSurchargeRepository.existsByFromClassAndToClassAndIsDeletedFalse(from, to)) {
            log.warn("Tạo phụ thu thất bại: cặp {} → {} đã tồn tại", from, to);
            throw new DuplicateResourceException(
                    "Đã có phụ thu cho cặp " + from + " → " + to);
        }
        SeatClassSurcharge entity = new SeatClassSurcharge();
        entity.setFromClass(from);
        entity.setToClass(to);
        applyRequest(entity, request);
        return entity;
    }

    @Override
    protected void updateEntity(SeatClassSurcharge entity, SeatClassSurchargeRequest request) {
        SeatClass from = parseClass(request.fromClass());
        SeatClass to = parseClass(request.toClass());
        // Cho phép đổi cặp, nhưng không được trùng bản ghi KHÁC.
        if (seatClassSurchargeRepository
                .existsByFromClassAndToClassAndIsDeletedFalseAndIdNot(from, to, entity.getId())) {
            log.warn("Sửa phụ thu id={} thất bại: cặp {} → {} đã thuộc bản ghi khác",
                    entity.getId(), from, to);
            throw new DuplicateResourceException(
                    "Đã có phụ thu cho cặp " + from + " → " + to);
        }
        entity.setFromClass(from);
        entity.setToClass(to);
        applyRequest(entity, request);
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private void applyRequest(SeatClassSurcharge entity, SeatClassSurchargeRequest request) {
        entity.setSurcharge(request.surcharge());
        entity.setIsActive(!"INACTIVE".equalsIgnoreCase(request.status()));
    }

    private static SeatClass parseClass(String value) {
        return SeatClass.valueOf(value.trim().toUpperCase());
    }

    @Override
    protected void softDelete(SeatClassSurcharge entity) {
        entity.setIsDeleted(true);
        entity.setIsActive(false);
        entity.setDeletedAt(LocalDateTime.now());
    }

    @Override
    protected void setActive(SeatClassSurcharge entity, boolean active) {
        entity.setIsActive(active);
    }

    @Override
    protected Page<SeatClassSurcharge> search(ListQuery query, Map<String, String> filters) {
        Pageable pageable = PageMapper.toPageable(query, "fromClass", SORT_WHITELIST);
        return seatClassSurchargeRepository.findAll(buildSpec(query, filters), pageable);
    }

    private Specification<SeatClassSurcharge> buildSpec(ListQuery query, Map<String, String> filters) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));   // BR-LIST-04

            String from = filters.get("from_class");
            if (from != null) {
                predicates.add(cb.equal(root.get("fromClass"), parseClass(from)));
            }
            String to = filters.get("to_class");
            if (to != null) {
                predicates.add(cb.equal(root.get("toClass"), parseClass(to)));
            }
            String status = filters.get("status");
            if (status != null) {
                predicates.add(cb.equal(root.get("isActive"), "ACTIVE".equalsIgnoreCase(status)));
            }

            String keyword = query.getKeywordOrNull();
            if (keyword != null) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fromClass").as(String.class)), like),
                        cb.like(cb.lower(root.get("toClass").as(String.class)), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

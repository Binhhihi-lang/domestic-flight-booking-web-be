package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.dto.request.masterdata.FareRuleRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.FareRuleResponse;
import com.example.domesticflightbookingwebbackend.entity.FareClass;
import com.example.domesticflightbookingwebbackend.entity.FareRule;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.mapper.FareRuleMapper;
import com.example.domesticflightbookingwebbackend.repository.BookingSegmentRepository;
import com.example.domesticflightbookingwebbackend.repository.FareClassRepository;
import com.example.domesticflightbookingwebbackend.repository.FareRuleRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Quy định hoàn vé theo version (Phase 2). Chỉ để HIỂN THỊ, không tự tính hoàn tiền. */
@Slf4j
@Service
public class FareRuleService extends BaseCrudService<FareRule, Long, FareRuleRequest, FareRuleResponse> {

    private static final String[] SORT_WHITELIST = {"version", "effective_from", "refund_percent"};

    private final FareRuleRepository fareRuleRepository;
    private final FareClassRepository fareClassRepository;
    private final BookingSegmentRepository bookingSegmentRepository;
    private final FareRuleMapper fareRuleMapper;

    public FareRuleService(
            FareRuleRepository repository,
            FareClassRepository fareClassRepository,
            BookingSegmentRepository bookingSegmentRepository,
            FareRuleMapper fareRuleMapper) {
        super(repository);
        this.fareRuleRepository = repository;
        this.fareClassRepository = fareClassRepository;
        this.bookingSegmentRepository = bookingSegmentRepository;
        this.fareRuleMapper = fareRuleMapper;
    }

    @Override
    protected String resourceName() {
        return "Quy định hoàn vé";
    }

    @Override
    protected FareRuleResponse toResponse(FareRule entity) {
        return fareRuleMapper.toResponse(entity);
    }

    @Override
    protected FareRule createEntity(FareRuleRequest request) {
        validateRefundConsistency(request);
        FareClass fareClass = resolveFareClass(request.fareClassId());
        int nextVersion = fareRuleRepository.findMaxVersion(fareClass.getId()) + 1;

        FareRule rule = new FareRule();
        rule.setFareClass(fareClass);
        rule.setVersion(nextVersion);
        applyMutableFields(rule, request);
        return rule;
    }

    @Override
    protected void updateEntity(FareRule entity, FareRuleRequest request) {
        if (bookingSegmentRepository.existsByFareRuleId(entity.getId())) {
            log.warn("Sửa quy định hoàn vé id={} bị từ chối: đã có đặt chỗ tham chiếu", entity.getId());
            throw new BusinessException(
                    "Quy định hoàn vé đã được áp dụng cho đặt chỗ — vui lòng tạo phiên bản mới");
        }
        validateRefundConsistency(request);
        applyMutableFields(entity, request);
    }

    private void validateRefundConsistency(FareRuleRequest request) {
        if (!Boolean.TRUE.equals(request.isRefundable())
                && request.refundPercent().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("Hạng vé không cho phép hoàn tiền thì phần trăm hoàn phải bằng 0");
        }
    }

    private void applyMutableFields(FareRule rule, FareRuleRequest request) {
        rule.setIsRefundable(request.isRefundable());
        rule.setRefundMinHoursBeforeDeparture(request.refundMinHoursBeforeDeparture());
        rule.setRefundPercent(request.refundPercent());
        rule.setDescription(request.description() == null ? null : request.description().trim());
        rule.setEffectiveFrom(request.effectiveFrom() != null ? request.effectiveFrom() : LocalDateTime.now());
        rule.setIsActive(!"INACTIVE".equalsIgnoreCase(request.status()));
        rule.setUpdatedAt(LocalDateTime.now());
    }

    private FareClass resolveFareClass(Long fareClassId) {
        return fareClassRepository.findById(fareClassId)
                .filter(fc -> !Boolean.TRUE.equals(fc.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Hạng vé không tồn tại"));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findOrThrow(id);
        if (bookingSegmentRepository.existsByFareRuleId(id)) {
            log.warn("Xóa quy định hoàn vé id={} bị từ chối: đã có đặt chỗ tham chiếu", id);
            throw new BusinessException("Quy định hoàn vé đã được áp dụng cho đặt chỗ — không thể xóa");
        }
        super.delete(id);
    }

    @Override
    protected void softDelete(FareRule entity) {
        entity.setIsDeleted(true);
        entity.setIsActive(false);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
    }

    @Override
    protected void setActive(FareRule entity, boolean active) {
        entity.setIsActive(active);
        entity.setUpdatedAt(LocalDateTime.now());
    }

    @Override
    protected Page<FareRule> search(ListQuery query, Map<String, String> filters) {
        Pageable pageable = PageMapper.toPageable(query, "effectiveFrom", SORT_WHITELIST);
        return fareRuleRepository.findAll(buildSpec(query, filters), pageable);
    }

    private Specification<FareRule> buildSpec(ListQuery query, Map<String, String> filters) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));

            String fareClassId = filters.get("fare_class_id");
            if (fareClassId != null) {
                predicates.add(cb.equal(root.get("fareClass").get("id"), Long.valueOf(fareClassId)));
            }

            String isRefundable = filters.get("is_refundable");
            if (isRefundable != null) {
                predicates.add(cb.equal(root.get("isRefundable"), Boolean.parseBoolean(isRefundable)));
            }

            String keyword = query.getKeywordOrNull();
            if (keyword != null) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like),
                        cb.like(cb.lower(root.get("fareClass").get("fareClassCode")), like),
                        cb.like(cb.lower(root.get("fareClass").get("fareClassName")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.dto.request.masterdata.SeatTypeRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.SeatTypeResponse;
import com.example.domesticflightbookingwebbackend.entity.SeatClass;
import com.example.domesticflightbookingwebbackend.entity.SeatType;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.DuplicateResourceException;
import com.example.domesticflightbookingwebbackend.mapper.SeatTypeMapper;
import com.example.domesticflightbookingwebbackend.repository.AircraftSeatRepository;
import com.example.domesticflightbookingwebbackend.repository.SeatTypeRepository;
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

/** SeatType — CRUD đầy đủ (v0.4), mục 2.6 API v0.2. */
@Slf4j
@Service
public class SeatTypeService extends BaseCrudService<SeatType, Long, SeatTypeRequest, SeatTypeResponse> {

    private static final String[] SORT_WHITELIST = {"seat_type_code", "seat_type_name"};

    private final SeatTypeRepository seatTypeRepository;
    private final AircraftSeatRepository aircraftSeatRepository;
    private final SeatTypeMapper seatTypeMapper;

    public SeatTypeService(SeatTypeRepository repository, AircraftSeatRepository aircraftSeatRepository,
                           SeatTypeMapper seatTypeMapper) {
        super(repository);
        this.seatTypeRepository = repository;
        this.aircraftSeatRepository = aircraftSeatRepository;
        this.seatTypeMapper = seatTypeMapper;
    }

    @Override
    protected String resourceName() {
        return "Loại ghế";
    }

    @Override
    protected SeatTypeResponse toResponse(SeatType entity) {
        return seatTypeMapper.toResponse(entity);
    }

    @Override
    protected SeatType createEntity(SeatTypeRequest request) {
        String code = request.seatTypeCode().trim().toUpperCase();
        if (seatTypeRepository.existsBySeatTypeCodeAndIsDeletedFalse(code)) {
            log.warn("Tạo loại ghế thất bại: mã {} đã tồn tại", code);
            throw new DuplicateResourceException("Mã loại ghế đã tồn tại: " + code);
        }
        SeatType seatType = new SeatType();
        seatType.setSeatTypeCode(code);
        applyRequest(seatType, request);
        return seatType;
    }

    @Override
    protected void updateEntity(SeatType entity, SeatTypeRequest request) {
        applyRequest(entity, request);   // seat_type_code không đổi
    }

    private void applyRequest(SeatType seatType, SeatTypeRequest request) {
        seatType.setSeatTypeName(request.seatTypeName().trim());
        seatType.setSeatClass(SeatClass.valueOf(request.seatClass().toUpperCase()));
        seatType.setColorHex(normalizeColor(request.colorHex()));
        seatType.setIsActive(!"INACTIVE".equalsIgnoreCase(request.status()));
    }

    /** Chuỗi trắng → null (bỏ đặt màu); hex chuẩn hoá về chữ HOA cho dễ so sánh. */
    private static String normalizeColor(String colorHex) {
        if (colorHex == null || colorHex.isBlank()) {
            return null;
        }
        return colorHex.trim().toUpperCase();
    }

    /** Loại ghế đang được sơ đồ máy bay dùng → từ chối xóa mềm (chỉ cho tắt is_active). */
    @Override
    @Transactional
    public void delete(Long id) {
        SeatType entity = findOrThrow(id);
        if (aircraftSeatRepository.existsBySeatTypeId(id)) {
            log.warn("Xóa loại ghế id={} bị từ chối: đang được sơ đồ ghế sử dụng", id);
            throw new BusinessException(
                    "Loại ghế đang được sử dụng trong sơ đồ máy bay — chỉ có thể tắt trạng thái");
        }
        super.delete(id);
    }

    @Override
    protected void softDelete(SeatType entity) {
        entity.setIsDeleted(true);
        entity.setIsActive(false);
        entity.setDeletedAt(LocalDateTime.now());
    }

    @Override
    protected void setActive(SeatType entity, boolean active) {
        entity.setIsActive(active);
    }

    @Override
    protected Page<SeatType> search(ListQuery query, Map<String, String> filters) {
        Pageable pageable = PageMapper.toPageable(query, "seatTypeCode", SORT_WHITELIST);
        return seatTypeRepository.findAll(buildSpec(query), pageable);
    }

    private Specification<SeatType> buildSpec(ListQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));   // BR-LIST-04

            String keyword = query.getKeywordOrNull();
            if (keyword != null) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("seatTypeCode")), like),
                        cb.like(cb.lower(root.get("seatTypeName")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

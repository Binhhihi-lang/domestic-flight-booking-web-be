package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.dto.request.masterdata.CityRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.CityResponse;
import com.example.domesticflightbookingwebbackend.entity.City;
import com.example.domesticflightbookingwebbackend.exception.DuplicateResourceException;
import com.example.domesticflightbookingwebbackend.mapper.CityMapper;
import com.example.domesticflightbookingwebbackend.repository.CityRepository;
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

/** City — master data (mục 2.5 API v0.2). */
@Slf4j
@Service
public class CityService extends BaseCrudService<City, Long, CityRequest, CityResponse> {

    private static final String[] SORT_WHITELIST = {"city_code", "city_name"};

    private final CityRepository cityRepository;
    private final CityMapper cityMapper;

    public CityService(CityRepository repository, CityMapper cityMapper) {
        super(repository);
        this.cityRepository = repository;
        this.cityMapper = cityMapper;
    }

    @Override
    protected String resourceName() {
        return "Thành phố";
    }

    @Override
    protected CityResponse toResponse(City entity) {
        return cityMapper.toResponse(entity);
    }

    @Override
    protected City createEntity(CityRequest request) {
        String code = request.cityCode().trim().toUpperCase();
        if (cityRepository.existsByCityCodeAndIsDeletedFalse(code)) {
            log.warn("Tạo thành phố thất bại: mã {} đã tồn tại", code);
            throw new DuplicateResourceException("Mã thành phố đã tồn tại: " + code);
        }
        City city = new City();
        city.setCityCode(code);
        applyRequest(city, request);
        return city;
    }

    @Override
    protected void updateEntity(City entity, CityRequest request) {
        applyRequest(entity, request);   // city_code không đổi
    }

    private void applyRequest(City city, CityRequest request) {
        city.setCityName(request.cityName().trim());
        city.setCountry(request.country().trim());
        city.setImageUrl(request.imageUrl());
        city.setIsActive(!"INACTIVE".equalsIgnoreCase(request.status()));
    }

    @Override
    protected void softDelete(City entity) {
        entity.setIsDeleted(true);
        entity.setIsActive(false);
        entity.setDeletedAt(LocalDateTime.now());
    }

    @Override
    protected void setActive(City entity, boolean active) {
        entity.setIsActive(active);
    }

    @Override
    protected Page<City> search(ListQuery query, Map<String, String> filters) {
        Pageable pageable = PageMapper.toPageable(query, "cityCode", SORT_WHITELIST);
        return cityRepository.findAll(buildSpec(query, filters), pageable);
    }

    private Specification<City> buildSpec(ListQuery query, Map<String, String> filters) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));   // BR-LIST-04

            String country = filters.get("country");
            if (country != null) {
                predicates.add(cb.equal(cb.lower(root.get("country")), country.toLowerCase()));
            }

            String keyword = query.getKeywordOrNull();
            if (keyword != null) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("cityCode")), like),
                        cb.like(cb.lower(root.get("cityName")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.request.masterdata.CityRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.CityResponse;
import com.example.domesticflightbookingwebbackend.entity.City;
import com.example.domesticflightbookingwebbackend.service.CityService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** City — master data (mục 2.5 API v0.2). GET công khai. */
@RestController
@RequestMapping("/api/v1/cities")
public class CityController extends BaseCrudController<City, Long, CityRequest, CityResponse> {

    public CityController(CityService service) {
        super(service);
    }

    @GetMapping
    public ApiResponse<PageData<CityResponse>> list(
            ListQuery query,
            @RequestParam(required = false) String country) {
        return list(query, filters("country", country));
    }

    /** GET detail công khai (P — CITY_READ_DETAIL). */
    @Override
    @GetMapping("/{id}")
    public ApiResponse<CityResponse> getById(@PathVariable Long id) {
        return super.getById(id);
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('CITY_CREATE')")
    public ResponseEntity<ApiResponse<CityResponse>> create(@Valid @RequestBody CityRequest request) {
        return super.create(request);
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CITY_UPDATE')")
    public ApiResponse<CityResponse> update(@PathVariable Long id, @Valid @RequestBody CityRequest request) {
        return super.update(id, request);
    }

    @Override
    @PutMapping("/{id}/active")
    @PreAuthorize("hasAuthority('CITY_UPDATE')")
    public ApiResponse<CityResponse> toggleActive(@PathVariable Long id, @RequestParam boolean value) {
        return super.toggleActive(id, value);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CITY_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return super.delete(id);
    }
}

package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.request.masterdata.AirportRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.AirportResponse;
import com.example.domesticflightbookingwebbackend.entity.Airport;
import com.example.domesticflightbookingwebbackend.service.AirportService;
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

/** Airport — master data (mục 2.5 API v0.2). GET công khai. */
@RestController
@RequestMapping("/api/v1/airports")
public class AirportController extends BaseCrudController<Airport, Long, AirportRequest, AirportResponse> {

    public AirportController(AirportService service) {
        super(service);
    }

    @GetMapping
    public ApiResponse<PageData<AirportResponse>> list(
            ListQuery query,
            @RequestParam(name = "city_id", required = false) String cityId) {
        return list(query, filters("city_id", cityId));
    }

    /** GET detail công khai (P — AIRPORT_READ_DETAIL). */
    @Override
    @GetMapping("/{id}")
    public ApiResponse<AirportResponse> getById(@PathVariable Long id) {
        return super.getById(id);
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('AIRPORT_CREATE')")
    public ResponseEntity<ApiResponse<AirportResponse>> create(@Valid @RequestBody AirportRequest request) {
        return super.create(request);
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('AIRPORT_UPDATE')")
    public ApiResponse<AirportResponse> update(@PathVariable Long id, @Valid @RequestBody AirportRequest request) {
        return super.update(id, request);
    }

    @Override
    @PutMapping("/{id}/active")
    @PreAuthorize("hasAuthority('AIRPORT_UPDATE')")
    public ApiResponse<AirportResponse> toggleActive(@PathVariable Long id, @RequestParam boolean value) {
        return super.toggleActive(id, value);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('AIRPORT_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return super.delete(id);
    }
}

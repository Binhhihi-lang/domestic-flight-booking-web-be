package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.dto.request.common.BulkRequest;
import com.example.domesticflightbookingwebbackend.dto.response.common.BulkResult;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.request.masterdata.AirlineRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.AirlineResponse;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import com.example.domesticflightbookingwebbackend.service.AirlineService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Airline — quản trị (mục 2.5 API v0.4). Mọi endpoint cần permission AIRLINE_*;
 * khách xem ở {@code /api/public/airlines}.
 */
@RestController
@RequestMapping("/api/admin/airlines")
public class AirlineController extends BaseCrudController<Airline, Long, AirlineRequest, AirlineResponse> {

    public AirlineController(AirlineService service) {
        super(service);
    }

    /** Filter riêng của module: status, country (mục 2.5 API v0.2). */
    @GetMapping
    @PreAuthorize("hasAuthority('AIRLINE_READ')")
    public ApiResponse<PageData<AirlineResponse>> list(
            ListQuery query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String country) {
        return list(query, filters("status", status, "country", country));
    }

    /** GET detail công khai (P — AIRLINE_READ_DETAIL): GUEST xem được. */
    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('AIRLINE_READ_DETAIL')")
    public ApiResponse<AirlineResponse> getById(@PathVariable Long id) {
        return super.getById(id);
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('AIRLINE_CREATE')")
    public ResponseEntity<ApiResponse<AirlineResponse>> create(@Valid @RequestBody AirlineRequest request) {
        return super.create(request);
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('AIRLINE_UPDATE')")
    public ApiResponse<AirlineResponse> update(@PathVariable Long id, @Valid @RequestBody AirlineRequest request) {
        return super.update(id, request);
    }

    @Override
    @PutMapping("/{id}/active")
    @PreAuthorize("hasAuthority('AIRLINE_UPDATE')")
    public ApiResponse<AirlineResponse> toggleActive(@PathVariable Long id, @RequestParam boolean value) {
        return super.toggleActive(id, value);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('AIRLINE_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return super.delete(id);
    }

    @Override
    @PatchMapping("/bulk")
    @PreAuthorize("hasAuthority('AIRLINE_UPDATE')")
    public ApiResponse<BulkResult> bulkToggle(@Valid @RequestBody BulkRequest request) {
        return super.bulkToggle(request);
    }

    @Override
    @DeleteMapping("/bulk")
    @PreAuthorize("hasAuthority('AIRLINE_DELETE')")
    public ApiResponse<BulkResult> bulkDelete(@Valid @RequestBody BulkRequest request) {
        return super.bulkDelete(request);
    }
}

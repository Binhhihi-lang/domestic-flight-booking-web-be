package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.dto.request.common.BulkRequest;
import com.example.domesticflightbookingwebbackend.dto.response.common.BulkResult;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.request.masterdata.AircraftRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.AircraftResponse;
import com.example.domesticflightbookingwebbackend.entity.Aircraft;
import com.example.domesticflightbookingwebbackend.service.AircraftService;
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

/** Aircraft — KHÔNG public, chỉ STAFF/ADMIN (mục 2.6 API v0.2). */
@RestController
@RequestMapping("/api/admin/aircrafts")
public class AircraftController extends BaseCrudController<Aircraft, Long, AircraftRequest, AircraftResponse> {

    public AircraftController(AircraftService service) {
        super(service);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AIRCRAFT_READ')")
    public ApiResponse<PageData<AircraftResponse>> list(ListQuery query) {
        return list(query, filters());
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('AIRCRAFT_READ_DETAIL')")
    public ApiResponse<AircraftResponse> getById(@PathVariable Long id) {
        return super.getById(id);
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('AIRCRAFT_CREATE')")
    public ResponseEntity<ApiResponse<AircraftResponse>> create(@Valid @RequestBody AircraftRequest request) {
        return super.create(request);
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('AIRCRAFT_UPDATE')")
    public ApiResponse<AircraftResponse> update(@PathVariable Long id, @Valid @RequestBody AircraftRequest request) {
        return super.update(id, request);
    }

    @Override
    @PutMapping("/{id}/active")
    @PreAuthorize("hasAuthority('AIRCRAFT_UPDATE')")
    public ApiResponse<AircraftResponse> toggleActive(@PathVariable Long id, @RequestParam boolean value) {
        return super.toggleActive(id, value);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('AIRCRAFT_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return super.delete(id);
    }

    @Override
    @PatchMapping("/bulk")
    @PreAuthorize("hasAuthority('AIRCRAFT_UPDATE')")
    public ApiResponse<BulkResult> bulkToggle(@Valid @RequestBody BulkRequest request) {
        return super.bulkToggle(request);
    }

    @Override
    @DeleteMapping("/bulk")
    @PreAuthorize("hasAuthority('AIRCRAFT_DELETE')")
    public ApiResponse<BulkResult> bulkDelete(@Valid @RequestBody BulkRequest request) {
        return super.bulkDelete(request);
    }
}

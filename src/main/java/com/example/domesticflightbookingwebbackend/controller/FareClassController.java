package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.request.common.BulkRequest;
import com.example.domesticflightbookingwebbackend.dto.request.masterdata.FareClassRequest;
import com.example.domesticflightbookingwebbackend.dto.response.common.BulkResult;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.FareClassResponse;
import com.example.domesticflightbookingwebbackend.entity.FareClass;
import com.example.domesticflightbookingwebbackend.service.FareClassService;
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

/** Hạng vé — quản trị CRUD (Phase 2). */
@RestController
@RequestMapping("/api/admin/fare-classes")
public class FareClassController extends BaseCrudController<FareClass, Long, FareClassRequest, FareClassResponse> {

    public FareClassController(FareClassService service) {
        super(service);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FARE_CLASS_READ')")
    public ApiResponse<PageData<FareClassResponse>> list(
            ListQuery query,
            @RequestParam(name = "airline_id", required = false) String airlineId,
            @RequestParam(name = "seat_class", required = false) String seatClass,
            @RequestParam(name = "status", required = false) String status) {
        return list(query, filters("airline_id", airlineId, "seat_class", seatClass, "status", status));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FARE_CLASS_READ_DETAIL')")
    public ApiResponse<FareClassResponse> getById(@PathVariable Long id) {
        return super.getById(id);
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('FARE_CLASS_CREATE')")
    public ResponseEntity<ApiResponse<FareClassResponse>> create(@Valid @RequestBody FareClassRequest request) {
        return super.create(request);
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FARE_CLASS_UPDATE')")
    public ApiResponse<FareClassResponse> update(@PathVariable Long id, @Valid @RequestBody FareClassRequest request) {
        return super.update(id, request);
    }

    @Override
    @PutMapping("/{id}/active")
    @PreAuthorize("hasAuthority('FARE_CLASS_UPDATE')")
    public ApiResponse<FareClassResponse> toggleActive(@PathVariable Long id, @RequestParam boolean value) {
        return super.toggleActive(id, value);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FARE_CLASS_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return super.delete(id);
    }

    @Override
    @PatchMapping("/bulk")
    @PreAuthorize("hasAuthority('FARE_CLASS_UPDATE')")
    public ApiResponse<BulkResult> bulkToggle(@Valid @RequestBody BulkRequest request) {
        return super.bulkToggle(request);
    }

    @Override
    @DeleteMapping("/bulk")
    @PreAuthorize("hasAuthority('FARE_CLASS_DELETE')")
    public ApiResponse<BulkResult> bulkDelete(@Valid @RequestBody BulkRequest request) {
        return super.bulkDelete(request);
    }
}

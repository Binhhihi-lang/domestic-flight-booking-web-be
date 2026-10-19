package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.dto.request.common.BulkRequest;
import com.example.domesticflightbookingwebbackend.dto.response.common.BulkResult;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.request.masterdata.SeatTypeRequest;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.SeatTypeResponse;
import com.example.domesticflightbookingwebbackend.entity.SeatType;
import com.example.domesticflightbookingwebbackend.service.SeatTypeService;
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

/** SeatType — CRUD đầy đủ, KHÔNG public (mục 2.6 API v0.2). */
@RestController
@RequestMapping("/api/admin/seat-types")
public class SeatTypeController extends BaseCrudController<SeatType, Long, SeatTypeRequest, SeatTypeResponse> {

    public SeatTypeController(SeatTypeService service) {
        super(service);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SEAT_CONFIG_READ')")
    public ApiResponse<PageData<SeatTypeResponse>> list(ListQuery query) {
        return list(query, filters());
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SEAT_CONFIG_READ_DETAIL')")
    public ApiResponse<SeatTypeResponse> getById(@PathVariable Long id) {
        return super.getById(id);
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('SEAT_CONFIG_CREATE')")
    public ResponseEntity<ApiResponse<SeatTypeResponse>> create(@Valid @RequestBody SeatTypeRequest request) {
        return super.create(request);
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SEAT_CONFIG_UPDATE')")
    public ApiResponse<SeatTypeResponse> update(@PathVariable Long id, @Valid @RequestBody SeatTypeRequest request) {
        return super.update(id, request);
    }

    @Override
    @PutMapping("/{id}/active")
    @PreAuthorize("hasAuthority('SEAT_CONFIG_UPDATE')")
    public ApiResponse<SeatTypeResponse> toggleActive(@PathVariable Long id, @RequestParam boolean value) {
        return super.toggleActive(id, value);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SEAT_CONFIG_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return super.delete(id);
    }

    @Override
    @PatchMapping("/bulk")
    @PreAuthorize("hasAuthority('SEAT_CONFIG_UPDATE')")
    public ApiResponse<BulkResult> bulkToggle(@Valid @RequestBody BulkRequest request) {
        return super.bulkToggle(request);
    }

    @Override
    @DeleteMapping("/bulk")
    @PreAuthorize("hasAuthority('SEAT_CONFIG_DELETE')")
    public ApiResponse<BulkResult> bulkDelete(@Valid @RequestBody BulkRequest request) {
        return super.bulkDelete(request);
    }
}

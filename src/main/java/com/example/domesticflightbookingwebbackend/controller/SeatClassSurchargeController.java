package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.request.common.BulkRequest;
import com.example.domesticflightbookingwebbackend.dto.request.masterdata.SeatClassSurchargeRequest;
import com.example.domesticflightbookingwebbackend.dto.response.common.BulkResult;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.SeatClassSurchargeResponse;
import com.example.domesticflightbookingwebbackend.entity.SeatClassSurcharge;
import com.example.domesticflightbookingwebbackend.service.SeatClassSurchargeService;
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
 * Phụ thu nâng hạng ghế — CRUD đầy đủ, không có bản public.
 *
 * <p>Quyền dùng chung nhóm {@code SEAT_CONFIG_*} vì cùng thuộc cấu hình ghế.
 * Hai method bulk phải tự khai {@code @PreAuthorize}: {@link BaseCrudController}
 * không gắn annotation quyền lên chúng.
 */
@RestController
@RequestMapping("/api/admin/seat-class-surcharges")
public class SeatClassSurchargeController
        extends BaseCrudController<SeatClassSurcharge, Long,
                SeatClassSurchargeRequest, SeatClassSurchargeResponse> {

    public SeatClassSurchargeController(SeatClassSurchargeService service) {
        super(service);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SEAT_CONFIG_READ')")
    public ApiResponse<PageData<SeatClassSurchargeResponse>> list(
            ListQuery query,
            @RequestParam(name = "from_class", required = false) String fromClass,
            @RequestParam(name = "to_class", required = false) String toClass,
            @RequestParam(name = "status", required = false) String status) {
        return list(query, filters("from_class", fromClass, "to_class", toClass, "status", status));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SEAT_CONFIG_READ_DETAIL')")
    public ApiResponse<SeatClassSurchargeResponse> getById(@PathVariable Long id) {
        return super.getById(id);
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('SEAT_CONFIG_CREATE')")
    public ResponseEntity<ApiResponse<SeatClassSurchargeResponse>> create(
            @Valid @RequestBody SeatClassSurchargeRequest request) {
        return super.create(request);
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SEAT_CONFIG_UPDATE')")
    public ApiResponse<SeatClassSurchargeResponse> update(
            @PathVariable Long id, @Valid @RequestBody SeatClassSurchargeRequest request) {
        return super.update(id, request);
    }

    @Override
    @PutMapping("/{id}/active")
    @PreAuthorize("hasAuthority('SEAT_CONFIG_UPDATE')")
    public ApiResponse<SeatClassSurchargeResponse> toggleActive(
            @PathVariable Long id, @RequestParam boolean value) {
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

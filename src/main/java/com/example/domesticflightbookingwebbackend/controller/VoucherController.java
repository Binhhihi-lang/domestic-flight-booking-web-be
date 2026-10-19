package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.dto.request.common.BulkRequest;
import com.example.domesticflightbookingwebbackend.dto.response.common.BulkResult;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.request.voucher.VoucherRequest;
import com.example.domesticflightbookingwebbackend.dto.response.voucher.VoucherResponse;
import com.example.domesticflightbookingwebbackend.entity.Voucher;
import com.example.domesticflightbookingwebbackend.service.VoucherService;
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

import java.util.List;
import java.util.Map;

/** Voucher — quản lý định nghĩa (ADMIN) + tặng voucher (mục 2.8 API v0.2). */
@RestController
@RequestMapping("/api/admin/vouchers")
public class VoucherController extends BaseCrudController<Voucher, Long, VoucherRequest, VoucherResponse> {

    private final VoucherService voucherService;

    public VoucherController(VoucherService service) {
        super(service);
        this.voucherService = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VOUCHER_READ')")
    public ApiResponse<PageData<VoucherResponse>> list(
            ListQuery query,
            @RequestParam(name = "is_active", required = false) String isActive,
            @RequestParam(name = "distribution_type", required = false) String distributionType,
            @RequestParam(name = "discount_type", required = false) String discountType) {
        return list(query, filters("is_active", isActive, "distribution_type", distributionType,
                "discount_type", discountType));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VOUCHER_READ_DETAIL')")
    public ApiResponse<VoucherResponse> getById(@PathVariable Long id) {
        return super.getById(id);
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('VOUCHER_CREATE')")
    public ResponseEntity<ApiResponse<VoucherResponse>> create(@Valid @RequestBody VoucherRequest request) {
        return super.create(request);
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('VOUCHER_UPDATE')")
    public ApiResponse<VoucherResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody VoucherRequest request) {
        return super.update(id, request);
    }

    @Override
    @PutMapping("/{id}/active")
    @PreAuthorize("hasAuthority('VOUCHER_UPDATE')")
    public ApiResponse<VoucherResponse> toggleActive(@PathVariable Long id, @RequestParam boolean value) {
        return super.toggleActive(id, value);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('VOUCHER_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return super.delete(id);
    }

    /** Tặng voucher ASSIGNED cho danh sách user (ADMIN). */
    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('VOUCHER_UPDATE')")
    public ApiResponse<Map<String, Integer>> assign(@PathVariable Long id,
                                                    @RequestBody AssignRequest request) {
        int count = voucherService.assign(id, request.userIds());
        return ApiResponse.success("Đã tặng voucher cho " + count + " người dùng",
                Map.of("assigned_count", count));
    }

    /** Body của POST /vouchers/{id}/assign. */
    public record AssignRequest(List<Long> userIds) {
    }

    @Override
    @PatchMapping("/bulk")
    @PreAuthorize("hasAuthority('VOUCHER_UPDATE')")
    public ApiResponse<BulkResult> bulkToggle(@Valid @RequestBody BulkRequest request) {
        return super.bulkToggle(request);
    }

    @Override
    @DeleteMapping("/bulk")
    @PreAuthorize("hasAuthority('VOUCHER_DELETE')")
    public ApiResponse<BulkResult> bulkDelete(@Valid @RequestBody BulkRequest request) {
        return super.bulkDelete(request);
    }
}

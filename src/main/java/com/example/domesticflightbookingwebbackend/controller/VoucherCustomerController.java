package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.dto.response.voucher.MyVoucherResponse;
import com.example.domesticflightbookingwebbackend.dto.response.voucher.VoucherStoreItem;
import com.example.domesticflightbookingwebbackend.entity.UserVoucher;
import com.example.domesticflightbookingwebbackend.security.SecurityUtils;
import com.example.domesticflightbookingwebbackend.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Voucher phía khách: Kho voucher, Nhận, Voucher của tôi (mục 2.8 API v0.2).
 * Mọi endpoint SELF lấy user từ token, KHÔNG nhận user_id từ client.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VoucherCustomerController {

    private final VoucherService voucherService;

    /** Kho voucher GLOBAL đang phát (kèm cờ claimed). */
    @GetMapping("/voucher-store")
    @PreAuthorize("hasAuthority('VOUCHER_CLAIM')")
    public ApiResponse<List<VoucherStoreItem>> voucherStore() {
        return ApiResponse.success(voucherService.getVoucherStore(SecurityUtils.getCurrentUserId()));
    }

    /** Khách bấm Nhận một voucher GLOBAL. */
    @PostMapping("/vouchers/{id}/claim")
    @PreAuthorize("hasAuthority('VOUCHER_CLAIM')")
    public ApiResponse<Map<String, Object>> claim(@PathVariable Long id) {
        UserVoucher uv = voucherService.claim(id, SecurityUtils.getCurrentUserId());
        return ApiResponse.success("Nhận voucher thành công",
                Map.of("user_voucher_id", uv.getId(), "status", uv.getStatus().name()));
    }

    /** Ví voucher của khách. */
    @GetMapping("/my-vouchers")
    @PreAuthorize("hasAuthority('VOUCHER_READ')")
    public ApiResponse<PageData<MyVoucherResponse>> myVouchers(ListQuery query) {
        int page = query.getPageOrDefault() - 1;
        int size = query.getSizeOrDefault();
        return ApiResponse.success(PageMapper.toPageData(
                voucherService.getMyVouchers(SecurityUtils.getCurrentUserId(), PageRequest.of(page, size))));
    }

    @GetMapping("/my-vouchers/{id}")
    @PreAuthorize("hasAuthority('VOUCHER_READ_DETAIL')")
    public ApiResponse<MyVoucherResponse> myVoucher(@PathVariable Long id) {
        return ApiResponse.success(voucherService.getMyVoucher(id, SecurityUtils.getCurrentUserId()));
    }
}

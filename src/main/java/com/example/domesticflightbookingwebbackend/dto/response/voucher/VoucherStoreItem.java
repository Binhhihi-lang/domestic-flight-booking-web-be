package com.example.domesticflightbookingwebbackend.dto.response.voucher;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Voucher trong Kho voucher — GET /voucher-store, kèm cờ claimed (mục 2.8). */
public record VoucherStoreItem(
        Long id,
        String voucherCode,
        String voucherName,
        String description,
        String discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscountAmount,
        BigDecimal minOrderAmount,
        String applicableTripType,
        LocalDate startDate,
        LocalDate endDate,
        Integer claimLimit,
        Integer claimedCount,
        boolean claimed
) {
}

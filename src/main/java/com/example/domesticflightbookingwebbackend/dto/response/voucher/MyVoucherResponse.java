package com.example.domesticflightbookingwebbackend.dto.response.voucher;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Voucher trong ví khách — GET /my-vouchers (mục 2.8 API v0.2). */
public record MyVoucherResponse(
        Long id,
        String status,
        String source,
        LocalDateTime receivedAt,
        LocalDateTime expiresAt,
        LocalDateTime usedAt,
        VoucherBrief voucher
) {
    /** Thông tin voucher rút gọn kèm trong ví. */
    public record VoucherBrief(
            Long id,
            String voucherCode,
            String voucherName,
            String description,
            String discountType,
            BigDecimal discountValue,
            BigDecimal minOrderAmount,
            String applicableTripType,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
    ) {
    }
}

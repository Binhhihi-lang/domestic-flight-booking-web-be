package com.example.domesticflightbookingwebbackend.dto.response.voucher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Voucher trả về client (mục 2.8 API v0.2). */
public record VoucherResponse(
        Long id,
        String voucherCode,
        String voucherName,
        String description,
        String distributionType,
        String discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscountAmount,
        BigDecimal minOrderAmount,
        String applicableTripType,
        LocalDate startDate,
        LocalDate endDate,
        Integer claimLimit,
        Integer claimedCount,
        String status,
        List<ScopeResponse> scopes,
        List<Long> excludedFlightIds
) {
    public record ScopeResponse(
            String targetType,
            Long airlineId,
            Long fareClassId,
            Long flightId,
            Long departureAirportId,
            Long arrivalAirportId
    ) {
    }
}

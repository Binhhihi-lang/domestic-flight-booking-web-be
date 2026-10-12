package com.example.domesticflightbookingwebbackend.dto.response.promotion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Promotion trả về client (mục 2.7 API v0.2). */
public record PromotionResponse(
        Long id,
        String promotionCode,
        String promotionName,
        String description,
        String imageUrl,
        String badgeLabel,
        String discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscountAmount,
        BigDecimal minFareAmount,
        String applicableTripType,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        List<ScopeResponse> scopes,
        List<Long> excludedFlightIds
) {
    /** Một dòng phạm vi trong response. */
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

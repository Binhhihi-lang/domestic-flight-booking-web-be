package com.example.domesticflightbookingwebbackend.dto.response.masterdata;

import java.math.BigDecimal;

/** Một dòng phụ thu nâng hạng trả về client. */
public record SeatClassSurchargeResponse(
        Long id,
        String fromClass,
        String toClass,
        BigDecimal surcharge,
        String status
) {
}

package com.example.domesticflightbookingwebbackend.dto.response.masterdata;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Quy định hoàn vé trả về client. Chỉ để HIỂN THỊ — hệ thống không tự tính tiền hoàn. */
public record FareRuleResponse(
        Long id,
        Long fareClassId,
        String fareClassCode,
        String fareClassName,
        String airlineCode,
        Integer version,
        Boolean isRefundable,
        Integer refundMinHoursBeforeDeparture,
        BigDecimal refundPercent,
        String description,
        LocalDateTime effectiveFrom,
        String status
) {
}

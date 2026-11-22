package com.example.domesticflightbookingwebbackend.dto.response.masterdata;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Quy định hoàn vé công khai cho khách tra cứu — chỉ trả version mới nhất đang hiệu lực của các hạng vé ACTIVE. */
public record FareRulePublicItem(
        Long id,
        Long fareClassId,
        String fareClassCode,
        String fareClassName,
        String seatClass,
        Long airlineId,
        String airlineCode,
        String airlineName,
        Integer version,
        Boolean isRefundable,
        Integer refundMinHoursBeforeDeparture,
        BigDecimal refundPercent,
        String description,
        LocalDateTime effectiveFrom
) {
}

package com.example.domesticflightbookingwebbackend.dto.response.flight;

import java.math.BigDecimal;

/**
 * Một hạng vé đã được tính sẵn giá cho 3 đối tượng hành khách (ADULT, CHILD 75%, INFANT 10%),
 * kèm thông tin hoàn vé và số ghế còn trống theo khoang của chuyến bay.
 */
public record PricedFareResponse(
        Long flightFareId,
        Long fareClassId,
        String fareClassCode,
        String fareClassName,
        String seatClass,
        BigDecimal baseFare,
        BigDecimal taxFee,
        BigDecimal adultPrice,
        BigDecimal childPrice,
        BigDecimal infantPrice,
        String currency,
        long seatsAvailable,
        boolean isRefundable,
        Integer refundMinHoursBeforeDeparture,
        BigDecimal refundPercent,
        String refundDescription
) {
}

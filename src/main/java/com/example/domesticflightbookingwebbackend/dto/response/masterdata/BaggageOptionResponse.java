package com.example.domesticflightbookingwebbackend.dto.response.masterdata;

import java.math.BigDecimal;

/** Gói hành lý ký gửi trả về client. */
public record BaggageOptionResponse(
        Long id,
        Long airlineId,
        String airlineCode,
        String airlineName,
        String name,
        BigDecimal weightKg,
        BigDecimal price,
        String status
) {
}

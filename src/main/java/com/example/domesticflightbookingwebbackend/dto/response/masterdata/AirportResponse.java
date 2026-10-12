package com.example.domesticflightbookingwebbackend.dto.response.masterdata;

import java.math.BigDecimal;

/** Airport trả về client (mục 2.5 API v0.2) — kèm {@code city} lồng. */
public record AirportResponse(
        Long id,
        String airportCode,
        String airportName,
        BigDecimal latitude,
        BigDecimal longitude,
        CityResponse city,
        String status
) {
}

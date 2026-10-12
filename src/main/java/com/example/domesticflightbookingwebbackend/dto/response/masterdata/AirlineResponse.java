package com.example.domesticflightbookingwebbackend.dto.response.masterdata;

/**
 * Airline trả về client (mục 2.5 API v0.2).
 * DB v0.4 lưu {@code is_active} boolean → trả {@code status} chuỗi ACTIVE/INACTIVE.
 */
public record AirlineResponse(
        Long id,
        String airlineCode,
        String airlineName,
        String country,
        String logoUrl,
        String status
) {
}

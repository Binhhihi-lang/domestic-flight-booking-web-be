package com.example.domesticflightbookingwebbackend.dto.response.masterdata;

/** Aircraft trả về client (mục 2.6 API v0.2). */
public record AircraftResponse(
        Long id,
        String aircraftCode,
        String model,
        String manufacturer,
        Integer seatCapacity,
        /** Cột sau đó là lối đi, vd "C" hoặc "C,E"; null = không có. */
        String aisleAfter,
        String status
) {
}

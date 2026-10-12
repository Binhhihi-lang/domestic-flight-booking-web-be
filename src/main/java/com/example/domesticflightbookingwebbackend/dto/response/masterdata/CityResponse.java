package com.example.domesticflightbookingwebbackend.dto.response.masterdata;

/** City trả về client (mục 2.5 API v0.2). */
public record CityResponse(
        Long id,
        String cityCode,
        String cityName,
        String country,
        String imageUrl,
        String status
) {
}

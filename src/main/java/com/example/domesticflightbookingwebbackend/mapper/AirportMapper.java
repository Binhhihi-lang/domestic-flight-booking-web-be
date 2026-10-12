package com.example.domesticflightbookingwebbackend.mapper;

import com.example.domesticflightbookingwebbackend.dto.response.masterdata.AirportResponse;
import com.example.domesticflightbookingwebbackend.entity.Airport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Airport ↔ DTO. Response lồng city (mục 2.5 API v0.2). */
@Component
@RequiredArgsConstructor
public class AirportMapper {

    private final CityMapper cityMapper;

    public AirportResponse toResponse(Airport airport) {
        return new AirportResponse(
                airport.getId(),
                airport.getAirportCode(),
                airport.getAirportName(),
                airport.getLatitude(),
                airport.getLongitude(),
                airport.getCity() == null ? null : cityMapper.toResponse(airport.getCity()),
                Boolean.TRUE.equals(airport.getIsActive()) ? "ACTIVE" : "INACTIVE"
        );
    }
}

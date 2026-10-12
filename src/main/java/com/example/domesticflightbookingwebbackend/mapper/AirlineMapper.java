package com.example.domesticflightbookingwebbackend.mapper;

import com.example.domesticflightbookingwebbackend.dto.response.masterdata.AirlineResponse;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import org.springframework.stereotype.Component;

/** Airline ↔ DTO. */
@Component
public class AirlineMapper {

    public AirlineResponse toResponse(Airline airline) {
        return new AirlineResponse(
                airline.getId(),
                airline.getAirlineCode(),
                airline.getAirlineName(),
                airline.getCountry(),
                airline.getLogoUrl(),
                Boolean.TRUE.equals(airline.getIsActive()) ? "ACTIVE" : "INACTIVE"
        );
    }
}

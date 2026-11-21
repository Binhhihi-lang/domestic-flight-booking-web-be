package com.example.domesticflightbookingwebbackend.mapper;

import com.example.domesticflightbookingwebbackend.dto.response.masterdata.FareClassResponse;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import com.example.domesticflightbookingwebbackend.entity.FareClass;
import org.springframework.stereotype.Component;

@Component
public class FareClassMapper {

    public FareClassResponse toResponse(FareClass fareClass) {
        Airline airline = fareClass.getAirline();
        return new FareClassResponse(
                fareClass.getId(),
                airline.getId(),
                airline.getAirlineCode(),
                airline.getAirlineName(),
                fareClass.getFareClassCode(),
                fareClass.getFareClassName(),
                fareClass.getSeatClass().name(),
                Boolean.TRUE.equals(fareClass.getIsActive()) ? "ACTIVE" : "INACTIVE"
        );
    }
}

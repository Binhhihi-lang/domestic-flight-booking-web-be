package com.example.domesticflightbookingwebbackend.mapper;

import com.example.domesticflightbookingwebbackend.dto.response.masterdata.BaggageOptionResponse;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import com.example.domesticflightbookingwebbackend.entity.BaggageOption;
import org.springframework.stereotype.Component;

@Component
public class BaggageOptionMapper {

    public BaggageOptionResponse toResponse(BaggageOption option) {
        Airline airline = option.getAirline();
        return new BaggageOptionResponse(
                option.getId(),
                airline.getId(),
                airline.getAirlineCode(),
                airline.getAirlineName(),
                option.getName(),
                option.getWeightKg(),
                option.getPrice(),
                Boolean.TRUE.equals(option.getIsActive()) ? "ACTIVE" : "INACTIVE"
        );
    }
}

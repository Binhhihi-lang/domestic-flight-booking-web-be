package com.example.domesticflightbookingwebbackend.mapper;

import com.example.domesticflightbookingwebbackend.dto.response.masterdata.AircraftResponse;
import com.example.domesticflightbookingwebbackend.entity.Aircraft;
import org.springframework.stereotype.Component;

@Component
public class AircraftMapper {

    public AircraftResponse toResponse(Aircraft aircraft) {
        return new AircraftResponse(
                aircraft.getId(),
                aircraft.getAircraftCode(),
                aircraft.getModel(),
                aircraft.getManufacturer(),
                aircraft.getSeatCapacity(),
                aircraft.getAisleAfter(),
                Boolean.TRUE.equals(aircraft.getIsActive()) ? "ACTIVE" : "INACTIVE"
        );
    }
}

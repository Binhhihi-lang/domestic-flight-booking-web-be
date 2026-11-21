package com.example.domesticflightbookingwebbackend.mapper;

import com.example.domesticflightbookingwebbackend.dto.response.flight.FlightFareResponse;
import com.example.domesticflightbookingwebbackend.entity.FareClass;
import com.example.domesticflightbookingwebbackend.entity.Flight;
import com.example.domesticflightbookingwebbackend.entity.FlightFare;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * FlightFare ↔ DTO (Phase 2).
 */
@Component
public class FlightFareMapper {

    public FlightFareResponse toResponse(
            FlightFare fare,
            BigDecimal adultPrice,
            BigDecimal childPrice,
            BigDecimal infantPrice) {
        Flight flight = fare.getFlight();
        FareClass fareClass = fare.getFareClass();
        return new FlightFareResponse(
                fare.getId(),
                flight.getId(),
                flight.getFlightNumber(),
                fareClass.getId(),
                fareClass.getFareClassCode(),
                fareClass.getFareClassName(),
                fareClass.getSeatClass().name(),
                fare.getBaseFare(),
                fare.getTaxFee(),
                adultPrice,
                childPrice,
                infantPrice,
                fare.getCurrency(),
                fare.getEffectiveFrom(),
                fare.getEffectiveTo(),
                fare.getCreatedAt()
        );
    }
}

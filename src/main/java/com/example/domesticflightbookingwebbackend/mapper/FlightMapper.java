package com.example.domesticflightbookingwebbackend.mapper;

import com.example.domesticflightbookingwebbackend.dto.response.flight.FlightDetailResponse;
import com.example.domesticflightbookingwebbackend.dto.response.flight.FlightResponse;
import com.example.domesticflightbookingwebbackend.dto.response.flight.FlightSearchResult;
import com.example.domesticflightbookingwebbackend.dto.response.flight.PricedFareResponse;
import com.example.domesticflightbookingwebbackend.entity.Aircraft;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import com.example.domesticflightbookingwebbackend.entity.Airport;
import com.example.domesticflightbookingwebbackend.entity.City;
import com.example.domesticflightbookingwebbackend.entity.Flight;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * Flight ↔ DTO (Phase 2).
 */
@Component
@RequiredArgsConstructor
public class FlightMapper {

    private final AirlineMapper airlineMapper;
    private final AircraftMapper aircraftMapper;
    private final AirportMapper airportMapper;

    public FlightResponse toResponse(Flight flight) {
        Integer fallbackCap = flight.getAircraft() != null ? flight.getAircraft().getSeatCapacity() : null;
        return toResponse(flight, fallbackCap, fallbackCap);
    }

    public FlightResponse toResponse(Flight flight, Integer seatCapacity, Integer availableSeats) {
        Flight parent = flight.getParentFlight();
        Integer effectiveCap = seatCapacity != null
                ? seatCapacity
                : (flight.getAircraft() != null ? flight.getAircraft().getSeatCapacity() : null);
        return new FlightResponse(
                flight.getId(),
                flight.getFlightNumber(),
                airlineMapper.toResponse(flight.getAirline()),
                aircraftMapper.toResponse(flight.getAircraft()),
                airportMapper.toResponse(flight.getDepartureAirport()),
                airportMapper.toResponse(flight.getArrivalAirport()),
                flight.getScheduledDeparture(),
                flight.getScheduledArrival(),
                computeDurationMinutes(flight),
                flight.getOperationStatus().name(),
                flight.getSalesStatus().name(),
                parent == null ? null : parent.getId(),
                parent == null ? null : parent.getFlightNumber(),
                effectiveCap,
                availableSeats,
                flight.getCreatedAt(),
                flight.getUpdatedAt()
        );
    }

    public FlightDetailResponse toDetailResponse(
            Flight flight,
            long totalSeats,
            long availableSeats,
            List<PricedFareResponse> fares) {
        return toDetailResponse(flight, totalSeats, availableSeats, fares, List.of());
    }

    public FlightDetailResponse toDetailResponse(
            Flight flight,
            long totalSeats,
            long availableSeats,
            List<PricedFareResponse> fares,
            List<FlightDetailResponse.FlightPassengerItem> passengers) {
        Flight parent = flight.getParentFlight();
        return new FlightDetailResponse(
                flight.getId(),
                flight.getFlightNumber(),
                airlineMapper.toResponse(flight.getAirline()),
                aircraftMapper.toResponse(flight.getAircraft()),
                airportMapper.toResponse(flight.getDepartureAirport()),
                airportMapper.toResponse(flight.getArrivalAirport()),
                flight.getScheduledDeparture(),
                flight.getScheduledArrival(),
                computeDurationMinutes(flight),
                flight.getOperationStatus().name(),
                flight.getSalesStatus().name(),
                parent == null ? null : parent.getId(),
                parent == null ? null : parent.getFlightNumber(),
                totalSeats,
                availableSeats,
                fares,
                passengers == null ? List.of() : passengers,
                flight.getCreatedAt(),
                flight.getUpdatedAt()
        );
    }

    public FlightSearchResult toSearchResult(Flight flight, List<PricedFareResponse> fares) {
        Airline airline = flight.getAirline();
        Aircraft aircraft = flight.getAircraft();
        Airport dep = flight.getDepartureAirport();
        Airport arr = flight.getArrivalAirport();
        City depCity = dep.getCity();
        City arrCity = arr.getCity();

        PricedFareResponse cheapest = fares.stream()
                .min(Comparator.comparing(PricedFareResponse::adultPrice))
                .orElse(null);

        BigDecimal lowestPrice = cheapest == null ? null : cheapest.adultPrice();
        String lowestCode = cheapest == null ? null : cheapest.fareClassCode();
        String lowestName = cheapest == null ? null : cheapest.fareClassName();

        return new FlightSearchResult(
                flight.getId(),
                flight.getId(),
                flight.getFlightNumber(),
                new FlightSearchResult.AirlineBrief(
                        airline.getId(),
                        airline.getAirlineCode(),
                        airline.getAirlineName(),
                        airline.getLogoUrl()),
                new FlightSearchResult.AircraftBrief(
                        aircraft.getId(),
                        aircraft.getAircraftCode(),
                        aircraft.getModel(),
                        aircraft.getSeatCapacity(),
                        aircraft.getAisleAfter()),
                new FlightSearchResult.AirportBrief(
                        dep.getId(),
                        dep.getAirportCode(),
                        dep.getAirportName(),
                        depCity == null ? null : depCity.getCityCode(),
                        depCity == null ? null : depCity.getCityName()),
                new FlightSearchResult.AirportBrief(
                        arr.getId(),
                        arr.getAirportCode(),
                        arr.getAirportName(),
                        arrCity == null ? null : arrCity.getCityCode(),
                        arrCity == null ? null : arrCity.getCityName()),
                flight.getScheduledDeparture(),
                flight.getScheduledArrival(),
                computeDurationMinutes(flight),
                flight.getOperationStatus().name(),
                flight.getSalesStatus().name(),
                lowestPrice,
                lowestCode,
                lowestName,
                fares
        );
    }

    private long computeDurationMinutes(Flight flight) {
        if (flight.getScheduledDeparture() == null || flight.getScheduledArrival() == null) {
            return 0L;
        }
        return Duration.between(flight.getScheduledDeparture(), flight.getScheduledArrival()).toMinutes();
    }
}

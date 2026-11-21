package com.example.domesticflightbookingwebbackend.dto.response.flight;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Một chuyến bay trong kết quả tìm kiếm công khai {@code GET /api/public/flights/search}
 * (khớp hợp đồng Phase 2 spec mục 7.5).
 */
public record FlightSearchResult(
        Long id,
        Long flightId,
        String flightNumber,
        AirlineBrief airline,
        AircraftBrief aircraft,
        AirportBrief departureAirport,
        AirportBrief arrivalAirport,
        LocalDateTime scheduledDeparture,
        LocalDateTime scheduledArrival,
        long durationMinutes,
        String operationStatus,
        String salesStatus,
        BigDecimal lowestAdultPrice,
        String lowestFareClassCode,
        String lowestFareClassName,
        List<PricedFareResponse> fares
) {
    public record AirlineBrief(
            Long id,
            String airlineCode,
            String airlineName,
            String logoUrl
    ) {
    }

    public record AircraftBrief(
            Long id,
            String aircraftCode,
            String model,
            Integer seatCapacity,
            String aisleAfter
    ) {
    }

    public record AirportBrief(
            Long id,
            String airportCode,
            String airportName,
            String cityCode,
            String cityName
    ) {
    }
}

package com.example.domesticflightbookingwebbackend.dto.response.flight;

import com.example.domesticflightbookingwebbackend.dto.response.masterdata.SeatTypeResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sơ đồ tồn ghế của một chuyến bay ({@code GET /api/admin/flights/{id}/seat-map},
 * {@code PUT /api/admin/flights/{id}/seat-inventory}).
 */
public record FlightSeatMapResponse(
        Long flightId,
        String flightNumber,
        Long aircraftId,
        String aircraftCode,
        String model,
        Integer seatCapacity,
        String aisleAfter,
        int totalSeats,
        int availableSeats,
        int heldSeats,
        int bookedSeats,
        int blockedSeats,
        List<FlightSeatEntry> seats
) {
    public record FlightSeatEntry(
            Long id,
            Long aircraftSeatId,
            String seatCode,
            Integer seatRow,
            String seatColumn,
            String status,
            BigDecimal seatSurcharge,
            SeatTypeResponse seatType
    ) {
    }
}

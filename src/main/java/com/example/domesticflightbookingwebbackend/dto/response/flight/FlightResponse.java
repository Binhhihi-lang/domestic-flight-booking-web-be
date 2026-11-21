package com.example.domesticflightbookingwebbackend.dto.response.flight;

import com.example.domesticflightbookingwebbackend.dto.response.masterdata.AircraftResponse;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.AirlineResponse;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.AirportResponse;

import java.time.LocalDateTime;

/**
 * Thông tin chuyến bay trả về cho danh sách quản trị / tạo / cập nhật (Phase 2).
 */
public record FlightResponse(
        Long id,
        String flightNumber,
        AirlineResponse airline,
        AircraftResponse aircraft,
        AirportResponse departureAirport,
        AirportResponse arrivalAirport,
        LocalDateTime scheduledDeparture,
        LocalDateTime scheduledArrival,
        long durationMinutes,
        String operationStatus,
        String salesStatus,
        Long parentFlightId,
        String parentFlightNumber,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

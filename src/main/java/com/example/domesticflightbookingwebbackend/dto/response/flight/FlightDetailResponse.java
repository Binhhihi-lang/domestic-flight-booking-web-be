package com.example.domesticflightbookingwebbackend.dto.response.flight;

import com.example.domesticflightbookingwebbackend.dto.response.masterdata.AircraftResponse;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.AirlineResponse;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.AirportResponse;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Chi tiết một chuyến bay (dùng cho cả GET /api/public/flights/{id} và GET /api/admin/flights/{id}),
 * kèm danh sách các hạng giá đang mở, thống kê số ghế và danh sách hành khách (chỉ trả ở vùng quản trị).
 */
public record FlightDetailResponse(
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
        long totalSeats,
        long availableSeats,
        List<PricedFareResponse> fares,
        List<FlightPassengerItem> passengers,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record FlightPassengerItem(
            String bookingCode,
            String pnrCode,
            String fullName,
            String passengerType,
            String seatCode,
            String fareClassName,
            String bookingStatus,
            boolean checkedIn
    ) {
    }
}

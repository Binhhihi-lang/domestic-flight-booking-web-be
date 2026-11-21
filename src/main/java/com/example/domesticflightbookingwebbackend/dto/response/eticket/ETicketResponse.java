package com.example.domesticflightbookingwebbackend.dto.response.eticket;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Thông tin một vé điện tử (E-Ticket) đã phát hành ({@code GET /api/bookings/{id}/e-tickets}).
 *
 * <p>Mỗi hành khách trên mỗi chặng bay nhận một vé điện tử riêng ({@code ticket_code} duy nhất).
 * Mã đặt chỗ PNR ({@code pnr_code}) lấy chung từ {@code bookings.booking_code}.
 * Hành khách em bé ({@code INFANT}) không có ghế ({@code seat_code = null}) nhưng vẫn có vé điện tử.
 */
public record ETicketResponse(
        Long id,
        Long bookingId,
        String pnrCode,
        String ticketCode,
        String status,
        LocalDateTime issuedAt,
        Long bookingSegmentId,
        Integer segmentOrder,
        Long flightId,
        String flightNumber,
        String airlineCode,
        String airlineName,
        String departureAirportCode,
        String departureAirportName,
        String departureCityName,
        String arrivalAirportCode,
        String arrivalAirportName,
        String arrivalCityName,
        LocalDateTime scheduledDeparture,
        LocalDateTime scheduledArrival,
        String fareClassCode,
        String fareClassName,
        String seatClass,
        Long passengerId,
        String passengerName,
        String passengerType,
        String gender,
        LocalDate birthDate,
        Long flightSeatId,
        String seatCode
) {
}

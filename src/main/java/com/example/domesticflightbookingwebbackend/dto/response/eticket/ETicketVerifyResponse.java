package com.example.domesticflightbookingwebbackend.dto.response.eticket;

import java.time.LocalDateTime;

/**
 * Kết quả xác thực vé điện tử công khai qua mã QR ({@code GET /api/public/e-tickets/verify}).
 * Chỉ trả về thông tin xác nhận hành trình & tình trạng vé; ẩn toàn bộ PII nhạy cảm
 * (email, SĐT, giấy tờ tùy thân, ngày sinh, tổng tiền).
 */
public record ETicketVerifyResponse(
        String ticketCode,
        String pnrCode,
        String status,
        LocalDateTime issuedAt,
        String airlineCode,
        String airlineName,
        String airlineLogoUrl,
        String flightNumber,
        String departureAirportCode,
        String departureCityName,
        String arrivalAirportCode,
        String arrivalCityName,
        LocalDateTime scheduledDeparture,
        LocalDateTime scheduledArrival,
        String fareClassName,
        String seatClass,
        String passengerName,
        String passengerType,
        String seatCode
) {
}

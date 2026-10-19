package com.example.domesticflightbookingwebbackend.dto.response.masterdata;

/** SeatType trả về client (mục 2.6 API v0.2). */
public record SeatTypeResponse(
        Long id,
        String seatTypeCode,
        String seatTypeName,
        String seatClass,
        String colorHex,
        String status
) {
}

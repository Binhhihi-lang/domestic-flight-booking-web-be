package com.example.domesticflightbookingwebbackend.dto.response.masterdata;

/** Hạng vé trả về client — kèm thông tin hãng để màn quản trị hiện cột "Hãng". */
public record FareClassResponse(
        Long id,
        Long airlineId,
        String airlineCode,
        String airlineName,
        String fareClassCode,
        String fareClassName,
        String seatClass,
        String status
) {
}

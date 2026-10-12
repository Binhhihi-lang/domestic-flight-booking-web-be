package com.example.domesticflightbookingwebbackend.dto.response.masterdata;

import java.util.List;

/** Sơ đồ ghế của máy bay (mục 2.6 API v0.2). */
public record SeatMapResponse(
        Long aircraftId,
        Integer seatCapacity,
        int configuredSeats,
        List<SeatEntry> seats
) {
    /** Một ghế trong sơ đồ, kèm loại ghế lồng. */
    public record SeatEntry(
            Long id,
            Integer seatRow,
            String seatColumn,
            SeatTypeResponse seatType
    ) {
    }
}

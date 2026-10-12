package com.example.domesticflightbookingwebbackend.mapper;

import com.example.domesticflightbookingwebbackend.dto.response.masterdata.SeatTypeResponse;
import com.example.domesticflightbookingwebbackend.entity.SeatType;
import org.springframework.stereotype.Component;

@Component
public class SeatTypeMapper {

    public SeatTypeResponse toResponse(SeatType seatType) {
        return new SeatTypeResponse(
                seatType.getId(),
                seatType.getSeatTypeCode(),
                seatType.getSeatTypeName(),
                seatType.getSeatClass().name(),
                Boolean.TRUE.equals(seatType.getIsActive()) ? "ACTIVE" : "INACTIVE"
        );
    }
}

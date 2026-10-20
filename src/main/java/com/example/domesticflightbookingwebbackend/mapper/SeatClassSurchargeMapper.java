package com.example.domesticflightbookingwebbackend.mapper;

import com.example.domesticflightbookingwebbackend.dto.response.masterdata.SeatClassSurchargeResponse;
import com.example.domesticflightbookingwebbackend.entity.SeatClassSurcharge;
import org.springframework.stereotype.Component;

@Component
public class SeatClassSurchargeMapper {

    public SeatClassSurchargeResponse toResponse(SeatClassSurcharge e) {
        return new SeatClassSurchargeResponse(
                e.getId(),
                e.getFromClass().name(),
                e.getToClass().name(),
                e.getSurcharge(),
                Boolean.TRUE.equals(e.getIsActive()) ? "ACTIVE" : "INACTIVE"
        );
    }
}

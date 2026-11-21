package com.example.domesticflightbookingwebbackend.mapper;

import com.example.domesticflightbookingwebbackend.dto.response.masterdata.FareRuleResponse;
import com.example.domesticflightbookingwebbackend.entity.FareClass;
import com.example.domesticflightbookingwebbackend.entity.FareRule;
import org.springframework.stereotype.Component;

@Component
public class FareRuleMapper {

    public FareRuleResponse toResponse(FareRule rule) {
        FareClass fareClass = rule.getFareClass();
        return new FareRuleResponse(
                rule.getId(),
                fareClass.getId(),
                fareClass.getFareClassCode(),
                fareClass.getFareClassName(),
                fareClass.getAirline().getAirlineCode(),
                rule.getVersion(),
                rule.getIsRefundable(),
                rule.getRefundMinHoursBeforeDeparture(),
                rule.getRefundPercent(),
                rule.getDescription(),
                rule.getEffectiveFrom(),
                Boolean.TRUE.equals(rule.getIsActive()) ? "ACTIVE" : "INACTIVE"
        );
    }
}

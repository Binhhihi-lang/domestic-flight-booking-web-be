package com.example.domesticflightbookingwebbackend.mapper;

import com.example.domesticflightbookingwebbackend.dto.response.masterdata.FareRulePublicItem;
import com.example.domesticflightbookingwebbackend.dto.response.masterdata.FareRuleResponse;
import com.example.domesticflightbookingwebbackend.entity.Airline;
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

    public FareRulePublicItem toPublicItem(FareRule rule) {
        FareClass fareClass = rule.getFareClass();
        Airline airline = fareClass.getAirline();
        return new FareRulePublicItem(
                rule.getId(),
                fareClass.getId(),
                fareClass.getFareClassCode(),
                fareClass.getFareClassName(),
                fareClass.getSeatClass() != null ? fareClass.getSeatClass().name() : null,
                airline != null ? airline.getId() : null,
                airline != null ? airline.getAirlineCode() : null,
                airline != null ? airline.getAirlineName() : null,
                rule.getVersion(),
                rule.getIsRefundable(),
                rule.getRefundMinHoursBeforeDeparture(),
                rule.getRefundPercent(),
                rule.getDescription(),
                rule.getEffectiveFrom()
        );
    }
}

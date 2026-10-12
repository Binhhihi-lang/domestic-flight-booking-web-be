package com.example.domesticflightbookingwebbackend.mapper;

import com.example.domesticflightbookingwebbackend.dto.response.masterdata.CityResponse;
import com.example.domesticflightbookingwebbackend.entity.City;
import org.springframework.stereotype.Component;

@Component
public class CityMapper {

    public CityResponse toResponse(City city) {
        return new CityResponse(
                city.getId(),
                city.getCityCode(),
                city.getCityName(),
                city.getCountry(),
                city.getImageUrl(),
                Boolean.TRUE.equals(city.getIsActive()) ? "ACTIVE" : "INACTIVE"
        );
    }
}

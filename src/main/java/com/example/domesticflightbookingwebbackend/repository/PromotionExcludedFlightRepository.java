package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.PromotionExcludedFlight;
import com.example.domesticflightbookingwebbackend.entity.PromotionExcludedFlightId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromotionExcludedFlightRepository
        extends JpaRepository<PromotionExcludedFlight, PromotionExcludedFlightId> {

    List<PromotionExcludedFlight> findByPromotionId(Long promotionId);
}

package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.PromotionScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromotionScopeRepository extends JpaRepository<PromotionScope, Long> {

    List<PromotionScope> findByPromotionId(Long promotionId);
}

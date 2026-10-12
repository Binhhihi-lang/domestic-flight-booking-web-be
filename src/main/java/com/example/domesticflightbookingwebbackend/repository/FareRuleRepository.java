package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.FareRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FareRuleRepository extends JpaRepository<FareRule, Long> {
}

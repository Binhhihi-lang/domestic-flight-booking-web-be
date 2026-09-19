package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.BaggageOption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BaggageOptionRepository extends JpaRepository<BaggageOption, Long> {
}
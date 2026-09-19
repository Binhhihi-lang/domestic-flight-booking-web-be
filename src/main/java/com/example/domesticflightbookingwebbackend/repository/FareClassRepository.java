package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.FareClass;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FareClassRepository extends JpaRepository<FareClass, Long> {
}
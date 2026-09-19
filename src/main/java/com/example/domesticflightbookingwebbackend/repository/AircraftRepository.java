package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.Aircraft;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AircraftRepository extends JpaRepository<Aircraft, Long> {
}
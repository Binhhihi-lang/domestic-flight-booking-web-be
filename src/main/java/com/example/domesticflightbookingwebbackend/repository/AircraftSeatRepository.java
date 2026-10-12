package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.AircraftSeat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AircraftSeatRepository extends JpaRepository<AircraftSeat, Long> {
}

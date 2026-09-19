package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.FlightSeat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlightSeatRepository extends JpaRepository<FlightSeat, Long> {
}
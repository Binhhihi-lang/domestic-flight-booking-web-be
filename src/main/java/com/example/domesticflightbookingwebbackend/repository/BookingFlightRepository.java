package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.BookingFlight;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingFlightRepository extends JpaRepository<BookingFlight, Long> {
}
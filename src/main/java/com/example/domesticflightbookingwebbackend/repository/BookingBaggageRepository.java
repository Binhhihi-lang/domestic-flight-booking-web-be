package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.BookingBaggage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingBaggageRepository extends JpaRepository<BookingBaggage, Long> {
}
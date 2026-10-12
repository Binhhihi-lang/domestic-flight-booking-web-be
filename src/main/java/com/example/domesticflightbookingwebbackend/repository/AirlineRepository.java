package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.Airline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AirlineRepository extends JpaRepository<Airline, Long> {

    Optional<Airline> findByAirlineCodeAndIsDeletedFalse(String airlineCode);

    boolean existsByAirlineCodeAndIsDeletedFalse(String airlineCode);
}

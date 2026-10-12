package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.Airport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface AirportRepository extends JpaRepository<Airport, Long>, JpaSpecificationExecutor<Airport> {

    Optional<Airport> findByAirportCodeAndIsDeletedFalse(String airportCode);

    boolean existsByAirportCodeAndIsDeletedFalse(String airportCode);
}

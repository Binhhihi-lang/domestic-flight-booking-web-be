package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.Airline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface AirlineRepository extends JpaRepository<Airline, Long>, JpaSpecificationExecutor<Airline> {

    Optional<Airline> findByAirlineCodeAndIsDeletedFalse(String airlineCode);

    boolean existsByAirlineCodeAndIsDeletedFalse(String airlineCode);
}

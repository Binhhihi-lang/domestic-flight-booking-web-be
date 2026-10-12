package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.Aircraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface AircraftRepository extends JpaRepository<Aircraft, Long>, JpaSpecificationExecutor<Aircraft> {

    Optional<Aircraft> findByAircraftCodeAndIsDeletedFalse(String aircraftCode);

    boolean existsByAircraftCodeAndIsDeletedFalse(String aircraftCode);
}

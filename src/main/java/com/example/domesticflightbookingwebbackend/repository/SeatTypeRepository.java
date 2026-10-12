package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.SeatType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SeatTypeRepository extends JpaRepository<SeatType, Long>, JpaSpecificationExecutor<SeatType> {

    Optional<SeatType> findBySeatTypeCodeAndIsDeletedFalse(String seatTypeCode);

    boolean existsBySeatTypeCodeAndIsDeletedFalse(String seatTypeCode);
}

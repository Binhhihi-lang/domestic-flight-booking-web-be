package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.FlightFare;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlightFareRepository extends JpaRepository<FlightFare, Long> {

    /** Hạng vé đã được định giá cho chuyến nào chưa — chặn xóa hạng vé. */
    boolean existsByFareClassId(Long fareClassId);
}

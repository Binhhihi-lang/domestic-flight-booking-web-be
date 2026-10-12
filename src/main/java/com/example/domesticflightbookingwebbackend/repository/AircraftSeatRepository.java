package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.AircraftSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AircraftSeatRepository extends JpaRepository<AircraftSeat, Long> {

    List<AircraftSeat> findByAircraftId(Long aircraftId);

    boolean existsBySeatTypeId(Long seatTypeId);

    /** Máy bay đã có ghế nào chưa — dùng để khóa sửa sơ đồ khi đã khai thác. */
    boolean existsByAircraftId(Long aircraftId);
}

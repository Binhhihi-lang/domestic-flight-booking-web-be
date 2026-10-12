package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.FlightSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlightSeatRepository extends JpaRepository<FlightSeat, Long> {

    /** Máy bay đã có ghế nào được dùng cho chuyến chưa — chặn sửa sơ đồ khi đã khai thác. */
    @Query("SELECT COUNT(fs) > 0 FROM FlightSeat fs WHERE fs.flight.aircraft.id = :aircraftId")
    boolean existsByFlightAircraftId(@Param("aircraftId") Long aircraftId);
}

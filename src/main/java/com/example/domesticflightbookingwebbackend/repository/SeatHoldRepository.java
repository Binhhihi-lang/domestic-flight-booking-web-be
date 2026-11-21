package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.SeatHold;
import com.example.domesticflightbookingwebbackend.entity.SeatHoldStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface SeatHoldRepository extends JpaRepository<SeatHold, Long> {

    @EntityGraph(attributePaths = {
            "passenger",
            "flightSeat", "flightSeat.flight",
            "flightSeat.aircraftSeat", "flightSeat.aircraftSeat.seatType"
    })
    List<SeatHold> findByBookingIdOrderByIdAsc(Long bookingId);

    @EntityGraph(attributePaths = {"passenger", "flightSeat", "flightSeat.flight"})
    List<SeatHold> findByBookingIdAndStatusIn(Long bookingId, Collection<SeatHoldStatus> statuses);

    /** Tìm các lượt giữ ghế đang ở trạng thái HOLD nhưng đã hết hạn 15 phút để job giải phóng. */
    @EntityGraph(attributePaths = {"booking", "flightSeat"})
    List<SeatHold> findByStatusAndHoldExpiresAtBefore(SeatHoldStatus status, LocalDateTime now);
}

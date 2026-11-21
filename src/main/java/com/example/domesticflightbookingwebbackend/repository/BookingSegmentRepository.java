package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.BookingSegment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BookingSegmentRepository extends JpaRepository<BookingSegment, Long> {

    /** Quy định hoàn vé đã được booking nào chốt chưa — chặn sửa/xóa version đó. */
    boolean existsByFareRuleId(Long fareRuleId);

    /** Chuyến bay đã phát sinh đặt chỗ chưa — chặn đổi máy bay hoặc xóa chuyến bay. */
    boolean existsByFlightId(Long flightId);

    @EntityGraph(attributePaths = {
            "flight", "flight.airline", "flight.aircraft",
            "flight.departureAirport", "flight.departureAirport.city",
            "flight.arrivalAirport", "flight.arrivalAirport.city",
            "fareClass", "fareRule", "promotion"
    })
    List<BookingSegment> findByBookingIdOrderBySegmentOrderAsc(Long bookingId);

    @EntityGraph(attributePaths = {
            "flight", "flight.airline",
            "flight.departureAirport", "flight.departureAirport.city",
            "flight.arrivalAirport", "flight.arrivalAirport.city",
            "fareClass"
    })
    List<BookingSegment> findByBookingIdInOrderBySegmentOrderAsc(Collection<Long> bookingIds);
}

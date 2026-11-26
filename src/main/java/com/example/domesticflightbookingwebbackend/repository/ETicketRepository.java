package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.ETicket;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ETicketRepository extends JpaRepository<ETicket, Long> {

    @EntityGraph(attributePaths = {
            "booking",
            "bookingSegment", "bookingSegment.flight", "bookingSegment.flight.airline",
            "bookingSegment.flight.departureAirport", "bookingSegment.flight.departureAirport.city",
            "bookingSegment.flight.arrivalAirport", "bookingSegment.flight.arrivalAirport.city",
            "bookingSegment.fareClass",
            "passenger",
            "flightSeat"
    })
    List<ETicket> findByBookingIdOrderByIdAsc(Long bookingId);

    @EntityGraph(attributePaths = {
            "booking",
            "bookingSegment", "bookingSegment.flight", "bookingSegment.flight.airline",
            "bookingSegment.flight.departureAirport", "bookingSegment.flight.departureAirport.city",
            "bookingSegment.flight.arrivalAirport", "bookingSegment.flight.arrivalAirport.city",
            "bookingSegment.fareClass",
            "passenger",
            "flightSeat"
    })
    Optional<ETicket> findByTicketCodeIgnoreCase(String ticketCode);

    boolean existsByBookingSegmentIdAndPassengerId(Long bookingSegmentId, Long passengerId);

    boolean existsByTicketCode(String ticketCode);
}

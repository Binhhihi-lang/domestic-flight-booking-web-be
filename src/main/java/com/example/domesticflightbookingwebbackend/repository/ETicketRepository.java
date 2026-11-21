package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.ETicket;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ETicketRepository extends JpaRepository<ETicket, Long> {

    @EntityGraph(attributePaths = {"bookingSegment", "passenger", "flightSeat"})
    List<ETicket> findByBookingIdOrderByIdAsc(Long bookingId);
}

package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.BookingBaggage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingBaggageRepository extends JpaRepository<BookingBaggage, Long> {

    @EntityGraph(attributePaths = {"passenger", "baggageOption", "baggageOption.airline"})
    List<BookingBaggage> findByBookingIdOrderByIdAsc(Long bookingId);

    void deleteByBookingId(Long bookingId);
}

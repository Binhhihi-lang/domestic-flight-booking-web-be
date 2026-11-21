package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.Passenger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PassengerRepository extends JpaRepository<Passenger, Long> {

    List<Passenger> findByBookingIdOrderByIdAsc(Long bookingId);

    List<Passenger> findByBookingIdInOrderByIdAsc(Collection<Long> bookingIds);

    long countByBookingId(Long bookingId);
}

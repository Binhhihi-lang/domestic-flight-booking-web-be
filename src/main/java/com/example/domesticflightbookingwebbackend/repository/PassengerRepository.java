package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.Passenger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PassengerRepository extends JpaRepository<Passenger, Long> {

    List<Passenger> findByBookingIdOrderByIdAsc(Long bookingId);

    List<Passenger> findByBookingIdInOrderByIdAsc(Collection<Long> bookingIds);

    long countByBookingId(Long bookingId);

    /** Đếm số hành khách theo từng booking trong 1 câu GROUP BY (chống N+1 cho báo cáo/dashboard). */
    @Query("""
            SELECT p.booking.id AS bookingId,
                   COUNT(p.id) AS passengerCount
            FROM Passenger p
            WHERE p.booking.id IN :bookingIds
            GROUP BY p.booking.id
            """)
    List<BookingPassengerCount> countByBookingIdIn(@Param("bookingIds") Collection<Long> bookingIds);

    interface BookingPassengerCount {
        Long getBookingId();
        Long getPassengerCount();
    }
}

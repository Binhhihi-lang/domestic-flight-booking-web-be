package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.Payment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"booking", "booking.user"})
    Optional<Payment> findWithBookingById(Long id);

    @EntityGraph(attributePaths = {"booking", "booking.user"})
    Optional<Payment> findByGatewayNameIgnoreCaseAndGatewayTransactionId(
            String gatewayName, String gatewayTransactionId);

    @EntityGraph(attributePaths = {"booking", "booking.user"})
    List<Payment> findByBookingIdOrderByCreatedAtDesc(Long bookingId);

    long countByBookingId(Long bookingId);
}

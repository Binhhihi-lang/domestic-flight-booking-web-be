package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.Booking;
import com.example.domesticflightbookingwebbackend.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    boolean existsByBookingCode(String bookingCode);

    @EntityGraph(attributePaths = "user")
    Optional<Booking> findWithUserById(Long id);

    @Override
    @EntityGraph(attributePaths = "user")
    Page<Booking> findAll(Specification<Booking> spec, Pageable pageable);

    /** Tìm các đơn đặt vé đang chờ thanh toán nhưng đã quá hạn 15 phút để job tự động chuyển sang EXPIRED. */
    List<Booking> findByStatusAndPaymentExpiresAtBefore(BookingStatus status, LocalDateTime now);

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    long countByStatusAndCreatedAtBetween(BookingStatus status, LocalDateTime from, LocalDateTime to);

    @EntityGraph(attributePaths = "user")
    List<Booking> findTop10ByOrderByCreatedAtDesc();
}

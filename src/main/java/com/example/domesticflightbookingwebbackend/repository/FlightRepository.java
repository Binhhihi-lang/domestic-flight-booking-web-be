package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.Flight;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FlightRepository extends JpaRepository<Flight, Long>, JpaSpecificationExecutor<Flight> {

    /** Không cho trùng số hiệu + giờ khởi hành trên các bản ghi chưa xóa mềm (V5). */
    boolean existsByFlightNumberIgnoreCaseAndScheduledDepartureAndIsDeletedFalse(
            String flightNumber, LocalDateTime scheduledDeparture);

    boolean existsByFlightNumberIgnoreCaseAndScheduledDepartureAndIsDeletedFalseAndIdNot(
            String flightNumber, LocalDateTime scheduledDeparture, Long id);

    /** Nạp kèm hãng, máy bay, sân bay đi/đến và thành phố trong 1 câu SQL chống N+1. */
    @Override
    @EntityGraph(attributePaths = {
            "airline", "aircraft",
            "departureAirport", "departureAirport.city",
            "arrivalAirport", "arrivalAirport.city",
            "parentFlight"
    })
    Page<Flight> findAll(Specification<Flight> spec, Pageable pageable);

    @EntityGraph(attributePaths = {
            "airline", "aircraft",
            "departureAirport", "departureAirport.city",
            "arrivalAirport", "arrivalAirport.city",
            "parentFlight"
    })
    Optional<Flight> findWithDetailsById(Long id);

    /**
     * Tìm các chuyến bay đang MỞ BÁN (OPEN), chưa hủy, chưa xóa mềm theo tuyến và khoảng giờ khởi hành trong ngày.
     * Sắp xếp theo giờ khởi hành tăng dần; dùng index {@code idx_flights_route_departure}.
     */
    @EntityGraph(attributePaths = {
            "airline", "aircraft",
            "departureAirport", "departureAirport.city",
            "arrivalAirport", "arrivalAirport.city"
    })
    @Query("""
            SELECT f FROM Flight f
            WHERE f.isDeleted = false
              AND f.salesStatus = com.example.domesticflightbookingwebbackend.entity.SalesStatus.OPEN
              AND f.operationStatus <> com.example.domesticflightbookingwebbackend.entity.FlightOperationStatus.CANCELLED
              AND f.departureAirport.id = :departureAirportId
              AND f.arrivalAirport.id = :arrivalAirportId
              AND f.scheduledDeparture >= :startOfDay
              AND f.scheduledDeparture < :endOfDay
            ORDER BY f.scheduledDeparture ASC
            """)
    List<Flight> findOpenFlightsForSearch(
            @Param("departureAirportId") Long departureAirportId,
            @Param("arrivalAirportId") Long arrivalAirportId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);

    /** Lấy danh sách chuyến bay trong khoảng thời gian (dùng cho Dashboard vận hành của Staff). */
    @EntityGraph(attributePaths = {
            "airline", "aircraft",
            "departureAirport", "departureAirport.city",
            "arrivalAirport", "arrivalAirport.city"
    })
    @Query("""
            SELECT f FROM Flight f
            WHERE f.isDeleted = false
              AND f.scheduledDeparture >= :from
              AND f.scheduledDeparture < :to
            ORDER BY f.scheduledDeparture ASC
            """)
    List<Flight> findFlightsInWindow(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}

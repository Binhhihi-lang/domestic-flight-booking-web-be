package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.FlightFare;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface FlightFareRepository extends JpaRepository<FlightFare, Long>, JpaSpecificationExecutor<FlightFare> {

    /** Hạng vé đã được định giá cho chuyến nào chưa — chặn xóa hạng vé. */
    boolean existsByFareClassId(Long fareClassId);

    /** Chuyến bay đã có bảng giá nào chưa — kiểm tra trước khi mở bán. */
    boolean existsByFlightId(Long flightId);

    @Override
    @EntityGraph(attributePaths = {"flight", "fareClass", "fareClass.airline"})
    Page<FlightFare> findAll(Specification<FlightFare> spec, Pageable pageable);

    /**
     * Kiểm tra chồng khoảng thời gian hiệu lực {@code [effectiveFrom, effectiveTo)} cho cùng
     * {@code (flight_id, fare_class_id)}. {@code effectiveTo = NULL} coi là vô cực.
     */
    @Query("""
            SELECT COUNT(ff) > 0 FROM FlightFare ff
            WHERE ff.flight.id = :flightId
              AND ff.fareClass.id = :fareClassId
              AND (:excludeId IS NULL OR ff.id <> :excludeId)
              AND ff.effectiveFrom < COALESCE(:effectiveTo, ff.effectiveFrom + 100000 YEAR)
              AND COALESCE(ff.effectiveTo, :effectiveFrom + 100000 YEAR) > :effectiveFrom
            """)
    boolean existsOverlappingFare(
            @Param("flightId") Long flightId,
            @Param("fareClassId") Long fareClassId,
            @Param("effectiveFrom") LocalDateTime effectiveFrom,
            @Param("effectiveTo") LocalDateTime effectiveTo,
            @Param("excludeId") Long excludeId);

    /** Lấy danh sách giá đang có hiệu lực tại thời điểm {@code now} cho một tập chuyến bay (chống N+1 khi search). */
    @EntityGraph(attributePaths = {"fareClass", "fareClass.airline"})
    @Query("""
            SELECT ff FROM FlightFare ff
            WHERE ff.flight.id IN :flightIds
              AND ff.fareClass.isDeleted = false
              AND ff.fareClass.isActive = true
              AND ff.effectiveFrom <= :now
              AND (ff.effectiveTo IS NULL OR ff.effectiveTo > :now)
            ORDER BY ff.baseFare ASC
            """)
    List<FlightFare> findActiveFaresByFlightIds(
            @Param("flightIds") Collection<Long> flightIds,
            @Param("now") LocalDateTime now);

    /** Lấy danh sách giá của một chuyến bay (dùng cho màn chi tiết chuyến bay). */
    @EntityGraph(attributePaths = {"fareClass", "fareClass.airline"})
    List<FlightFare> findByFlightIdOrderByBaseFareAsc(Long flightId);

    /** Lấy dòng giá đang có hiệu lực tại thời điểm {@code now} cho một cặp {@code (flight_id, fare_class_id)}. */
    @Query("""
            SELECT ff FROM FlightFare ff
            WHERE ff.flight.id = :flightId
              AND ff.fareClass.id = :fareClassId
              AND ff.effectiveFrom <= :now
              AND (ff.effectiveTo IS NULL OR ff.effectiveTo > :now)
            ORDER BY ff.effectiveFrom DESC
            """)
    List<FlightFare> findActiveFaresByFlightAndFareClass(
            @Param("flightId") Long flightId,
            @Param("fareClassId") Long fareClassId,
            @Param("now") LocalDateTime now);
}

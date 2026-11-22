package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.FlightSeat;
import com.example.domesticflightbookingwebbackend.entity.FlightSeatStatus;
import com.example.domesticflightbookingwebbackend.entity.SeatClass;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FlightSeatRepository extends JpaRepository<FlightSeat, Long> {

    /** Máy bay đã có ghế nào được dùng cho chuyến chưa — chặn sửa sơ đồ khi đã khai thác. */
    @Query("SELECT COUNT(fs) > 0 FROM FlightSeat fs WHERE fs.flight.aircraft.id = :aircraftId")
    boolean existsByFlightAircraftId(@Param("aircraftId") Long aircraftId);

    boolean existsByFlightId(Long flightId);

    long countByFlightId(Long flightId);

    long countByFlightIdAndStatus(Long flightId, FlightSeatStatus status);

    /** Lấy toàn bộ tồn ghế của chuyến bay kèm ghế máy bay và loại ghế trong 1 query. */
    @EntityGraph(attributePaths = {"aircraftSeat", "aircraftSeat.seatType"})
    List<FlightSeat> findByFlightId(Long flightId);

    /** Lấy một ghế chuyến bay kèm chuyến bay, ghế máy bay và loại ghế (dùng khi giữ ghế để kiểm tra khoang). */
    @EntityGraph(attributePaths = {"flight", "aircraftSeat", "aircraftSeat.seatType"})
    java.util.Optional<FlightSeat> findWithDetailsById(Long id);

    /**
     * Đếm số ghế trống (AVAILABLE) theo chuyến và khoang ({@code seat_class}) cho tập chuyến bay
     * (dùng khi tìm kiếm chuyến bay — 1 câu GROUP BY cho toàn bộ kết quả, không N+1).
     */
    @Query("""
            SELECT fs.flight.id AS flightId,
                   fs.aircraftSeat.seatType.seatClass AS seatClass,
                   COUNT(fs.id) AS availableCount
            FROM FlightSeat fs
            WHERE fs.flight.id IN :flightIds
              AND fs.status = com.example.domesticflightbookingwebbackend.entity.FlightSeatStatus.AVAILABLE
            GROUP BY fs.flight.id, fs.aircraftSeat.seatType.seatClass
            """)
    List<FlightCabinAvailability> countAvailableSeatsByFlightIdsAndCabin(
            @Param("flightIds") Collection<Long> flightIds);

    interface FlightCabinAvailability {
        Long getFlightId();
        SeatClass getSeatClass();
        Long getAvailableCount();
    }

    /** Đếm số lượng ghế theo từng trạng thái cho một tập chuyến bay (dùng cho Dashboard vận hành & tổng quan). */
    @Query("""
            SELECT fs.flight.id AS flightId,
                   fs.status AS status,
                   COUNT(fs.id) AS seatCount
            FROM FlightSeat fs
            WHERE fs.flight.id IN :flightIds
            GROUP BY fs.flight.id, fs.status
            """)
    List<FlightStatusCount> countSeatsByFlightIdsAndStatus(
            @Param("flightIds") Collection<Long> flightIds);

    interface FlightStatusCount {
        Long getFlightId();
        FlightSeatStatus getStatus();
        Long getSeatCount();
    }

    /** Tổng hợp tổng số ghế & số ghế còn trống cho một trang chuyến bay trong 1 câu GROUP BY (chống N+1). */
    @Query("""
            SELECT fs.flight.id AS flightId,
                   COUNT(fs.id) AS totalSeats,
                   SUM(CASE WHEN fs.status = com.example.domesticflightbookingwebbackend.entity.FlightSeatStatus.AVAILABLE THEN 1L ELSE 0L END) AS availableSeats
            FROM FlightSeat fs
            WHERE fs.flight.id IN :flightIds
            GROUP BY fs.flight.id
            """)
    List<FlightSeatSummary> summarizeSeatsByFlightIds(@Param("flightIds") Collection<Long> flightIds);

    interface FlightSeatSummary {
        Long getFlightId();
        Long getTotalSeats();
        Long getAvailableSeats();
    }

    /**
     * Khóa ghế nguyên tử chống tranh chấp (DB v0.5 mục 5.2):
     * chỉ đổi sang HOLD và tăng version nếu ghế đang AVAILABLE.
     *
     * @return số dòng cập nhật (1 = giữ thành công, 0 = đã bị giữ/bán/khóa)
     */
    @Modifying
    @Query("""
            UPDATE FlightSeat fs
            SET fs.status = com.example.domesticflightbookingwebbackend.entity.FlightSeatStatus.HOLD,
                fs.version = fs.version + 1
            WHERE fs.id = :flightSeatId
              AND fs.status = com.example.domesticflightbookingwebbackend.entity.FlightSeatStatus.AVAILABLE
            """)
    int holdSeatAtomically(@Param("flightSeatId") Long flightSeatId);

    /** Giải phóng ghế đang HOLD về AVAILABLE. */
    @Modifying
    @Query("""
            UPDATE FlightSeat fs
            SET fs.status = com.example.domesticflightbookingwebbackend.entity.FlightSeatStatus.AVAILABLE,
                fs.version = fs.version + 1
            WHERE fs.id = :flightSeatId
              AND fs.status = com.example.domesticflightbookingwebbackend.entity.FlightSeatStatus.HOLD
            """)
    int releaseHeldSeat(@Param("flightSeatId") Long flightSeatId);
}

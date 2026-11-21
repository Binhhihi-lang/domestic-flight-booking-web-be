package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.BookingSegment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingSegmentRepository extends JpaRepository<BookingSegment, Long> {

    /** Quy định hoàn vé đã được booking nào chốt chưa — chặn sửa/xóa version đó. */
    boolean existsByFareRuleId(Long fareRuleId);

    /** Chuyến bay đã phát sinh đặt chỗ chưa — chặn đổi máy bay hoặc xóa chuyến bay. */
    boolean existsByFlightId(Long flightId);
}

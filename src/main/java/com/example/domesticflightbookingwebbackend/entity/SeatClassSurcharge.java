package com.example.domesticflightbookingwebbackend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Phụ thu khi khách chọn ghế khác hạng với hạng vé đã mua.
 *
 * <p>Mặc định dùng chung cho mọi chuyến; {@code flight_seats.seat_surcharge}
 * ghi đè theo từng chuyến. Không có dòng (fromClass, toClass) nghĩa là CHƯA CÓ
 * CHÍNH SÁCH — không được mặc định 0.
 */
@Getter
@Setter
@Entity
@Table(name = "seat_class_surcharges")
public class SeatClassSurcharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Hạng VÉ khách đã mua. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_class", nullable = false, length = 20)
    private SeatClass fromClass;

    /** Hạng GHẾ vật lý khách chọn. */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_class", nullable = false, length = 20)
    private SeatClass toClass;

    @Column(name = "surcharge", nullable = false, precision = 12, scale = 2)
    private BigDecimal surcharge = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}

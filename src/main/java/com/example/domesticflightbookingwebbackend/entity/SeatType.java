package com.example.domesticflightbookingwebbackend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "seat_types")
public class SeatType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seat_type_code", nullable = false, length = 30)
    private String seatTypeCode;

    @Column(name = "seat_type_name", nullable = false, length = 100)
    private String seatTypeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_class", nullable = false, length = 20)
    private SeatClass seatClass;

    /** Mã màu #RRGGBB trên sơ đồ ghế; null = FE dùng màu mặc định theo seat_class. */
    @Column(name = "color_hex", length = 7)
    private String colorHex;

    /** Phụ thu chọn ghế mặc định (VND); flight_seats.seat_surcharge có thể ghi đè. */
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

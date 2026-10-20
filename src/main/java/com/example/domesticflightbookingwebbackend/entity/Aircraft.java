package com.example.domesticflightbookingwebbackend.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "aircrafts")
public class Aircraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aircraft_code", nullable = false, length = 30)
    private String aircraftCode;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "manufacturer", length = 100)
    private String manufacturer;

    @Column(name = "seat_capacity", nullable = false)
    private Integer seatCapacity;

    /**
     * Cột mà SAU ĐÓ là lối đi, phân tách bằng dấu phẩy — vd "C" (3-3) hoặc
     * "C,E" (3-2-2). NULL = cabin không có lối đi giữa.
     */
    @Column(name = "aisle_after", length = 20)
    private String aisleAfter;

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

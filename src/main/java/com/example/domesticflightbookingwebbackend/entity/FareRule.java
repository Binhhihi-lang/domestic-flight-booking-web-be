package com.example.domesticflightbookingwebbackend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Quy định/quyền lợi của một loại vé, có version (chính sách hoàn vé). */
@Getter
@Setter
@Entity
@Table(name = "fare_rules")
public class FareRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fare_class_id", nullable = false)
    private FareClass fareClass;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "is_refundable", nullable = false)
    private Boolean isRefundable;

    @Column(name = "refund_min_hours_before_departure", nullable = false)
    private Integer refundMinHoursBeforeDeparture;

    @Column(name = "refund_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal refundPercent;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

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

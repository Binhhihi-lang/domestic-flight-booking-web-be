package com.example.domesticflightbookingwebbackend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "promotions")
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "promotion_code", nullable = false, length = 50)
    private String promotionCode;

    @Column(name = "promotion_name", nullable = false, length = 150)
    private String promotionName;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 10)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    /** Trần giảm cho MỖI dòng vé (chỉ có ý nghĩa với PERCENT). */
    @Column(name = "max_discount_amount", precision = 12, scale = 2)
    private BigDecimal maxDiscountAmount;

    /** Giá dòng vé tối thiểu (đã gồm thuế phí) mới được giảm. */
    @Column(name = "min_fare_amount", precision = 12, scale = 2)
    private BigDecimal minFareAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "applicable_trip_type", nullable = false, length = 12)
    private TripTypeCondition applicableTripType = TripTypeCondition.ANY;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

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

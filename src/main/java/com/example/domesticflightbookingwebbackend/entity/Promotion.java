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

    /** Ảnh hiển thị trên thẻ khuyến mãi ở trang chủ. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** Dòng mô tả ngắn trên thẻ khuyến mãi. */
    @Column(name = "description", length = 500)
    private String description;

    /** Nhãn marketing ngắn ("Giá sốc", "Đồng giá") — ADMIN đặt, FE không suy ra được. */
    @Column(name = "badge_label", length = 30)
    private String badgeLabel;

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

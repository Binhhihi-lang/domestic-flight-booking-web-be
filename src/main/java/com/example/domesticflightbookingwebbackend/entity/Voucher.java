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
@Table(name = "vouchers")
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã nội bộ để ADMIN tra cứu; khách KHÔNG dùng mã này để nhập. */
    @Column(name = "voucher_code", nullable = false, length = 50)
    private String voucherCode;

    @Column(name = "voucher_name", nullable = false, length = 150)
    private String voucherName;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "distribution_type", nullable = false, length = 10)
    private VoucherDistributionType distributionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 10)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    /** Trần giảm cho CẢ booking (voucher PERCENT). */
    @Column(name = "max_discount_amount", precision = 12, scale = 2)
    private BigDecimal maxDiscountAmount;

    /** Tổng giá vé tối thiểu (sau khuyến mãi) mới được dùng voucher. */
    @Column(name = "min_order_amount", precision = 12, scale = 2)
    private BigDecimal minOrderAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "applicable_trip_type", nullable = false, length = 12)
    private TripTypeCondition applicableTripType = TripTypeCondition.ANY;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Tổng số lượt được nhận, chỉ áp cho GLOBAL (NULL = không giới hạn). */
    @Column(name = "claim_limit")
    private Integer claimLimit;

    @Column(name = "claimed_count", nullable = false)
    private Integer claimedCount = 0;

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

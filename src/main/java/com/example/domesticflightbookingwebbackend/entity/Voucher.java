package com.example.domesticflightbookingwebbackend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Column(name = "voucher_code", nullable = false, length = 50, unique = true)
    private String voucherCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_type", nullable = false, length = 20)
    private DiscountType voucherType;

    @Column(name = "value_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal valueAmount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "usage_limit", nullable = true)
    private Integer usageLimit;

    @Column(name = "per_user_limit", nullable = true)
    private Integer perUserLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private VoucherScope scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LifecycleStatus status = LifecycleStatus.ACTIVE;
}
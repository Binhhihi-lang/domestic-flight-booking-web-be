package com.example.domesticflightbookingwebbackend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Giá bán NGƯỜI LỚN của một loại vé trên một chuyến, theo khoảng thời gian hiệu lực.
 *  Giá trẻ em/em bé tính bằng tỉ lệ cố định từ giá người lớn (xem PricingProperties). */
@Getter
@Setter
@Entity
@Table(name = "flight_fares")
public class FlightFare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fare_class_id", nullable = false)
    private FareClass fareClass;

    @Column(name = "base_fare", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseFare;

    @Column(name = "tax_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxFee = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    /** NULL = chưa kết thúc hiệu lực. */
    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

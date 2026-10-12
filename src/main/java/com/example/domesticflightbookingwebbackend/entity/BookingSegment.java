package com.example.domesticflightbookingwebbackend.entity;

import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Một chặng bay trong booking (một chiều 1 chặng, khứ hồi 2 chặng).
 *  Chụp lại giá vé, khuyến mãi và quy định hoàn vé tại thời điểm đặt. */
@Getter
@Setter
@Entity
@Table(name = "booking_segments")
public class BookingSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @Column(name = "segment_order", nullable = false)
    private Integer segmentOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fare_class_id", nullable = false)
    private FareClass fareClass;

    /** Version quy định vé áp dụng lúc đặt (dùng tính hoàn tiền). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fare_rule_id", nullable = false)
    private FareRule fareRule;

    @Column(name = "fare_amount_at_booking", nullable = false, precision = 12, scale = 2)
    private BigDecimal fareAmountAtBooking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    @Column(name = "promotion_discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal promotionDiscountAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "VND";
}

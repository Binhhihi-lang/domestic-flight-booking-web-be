package com.example.domesticflightbookingwebbackend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Một lần đặt vé của khách (một chiều hoặc khứ hồi). booking_code chính là PNR. */
@Getter
@Setter
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_code", nullable = false, length = 30, unique = true)
    private String bookingCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "trip_type", nullable = false, length = 12)
    private TripType tripType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING_PAYMENT;

    /** Tổng giá vé gốc (đã gồm thuế phí, trước giảm). */
    @Column(name = "fare_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal fareAmount = BigDecimal.ZERO;

    @Column(name = "promotion_discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal promotionDiscountAmount = BigDecimal.ZERO;

    @Column(name = "voucher_discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal voucherDiscountAmount = BigDecimal.ZERO;

    @Column(name = "seat_surcharge_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal seatSurchargeAmount = BigDecimal.ZERO;

    @Column(name = "baggage_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal baggageAmount = BigDecimal.ZERO;

    /** = fare_amount − promotion_discount − voucher_discount + seat_surcharge + baggage. */
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "VND";

    /** Hạn thanh toán / giữ ghế = lúc tạo + 15 phút. */
    @Column(name = "payment_expires_at", nullable = false)
    private LocalDateTime paymentExpiresAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "applied_refund_percent", precision = 5, scale = 2)
    private BigDecimal appliedRefundPercent;

    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}

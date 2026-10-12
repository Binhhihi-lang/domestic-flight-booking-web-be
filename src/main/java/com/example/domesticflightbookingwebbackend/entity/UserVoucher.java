package com.example.domesticflightbookingwebbackend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Ví voucher của khách: mỗi dòng là một voucher khách đang giữ (kiêm lịch sử dùng). */
@Getter
@Setter
@Entity
@Table(name = "user_vouchers")
public class UserVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private UserVoucherStatus status = UserVoucherStatus.AVAILABLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private UserVoucherSource source = UserVoucherSource.CLAIMED;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt = LocalDateTime.now();

    /** Hạn dùng, chép từ vouchers.end_date lúc nhận. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "used_at")
    private LocalDateTime usedAt;
}

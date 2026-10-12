package com.example.domesticflightbookingwebbackend.entity;

import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Gói hành lý mà từng hành khách đã chọn trong booking. */
@Getter
@Setter
@Entity
@Table(name = "booking_baggages")
public class BookingBaggage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "baggage_option_id", nullable = false)
    private BaggageOption baggageOption;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;

    /** Giá một gói tại thời điểm đặt. */
    @Column(name = "unit_price_at_booking", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceAtBooking;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "VND";
}

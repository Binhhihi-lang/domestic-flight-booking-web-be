package com.example.domesticflightbookingwebbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Phạm vi áp dụng của voucher (hãng / hạng vé / tuyến / chuyến). */
@Getter
@Setter
@Entity
@Table(name = "voucher_scopes")
public class VoucherScope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 15)
    private ScopeTargetType targetType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "airline_id")
    private Airline airline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fare_class_id")
    private FareClass fareClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id")
    private Flight flight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departure_airport_id")
    private Airport departureAirport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arrival_airport_id")
    private Airport arrivalAirport;
}

package com.example.domesticflightbookingwebbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Chuyến bay bị loại trừ khỏi một voucher. */
@Getter
@Setter
@Entity
@Table(name = "voucher_excluded_flights")
@IdClass(VoucherExcludedFlightId.class)
public class VoucherExcludedFlight {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;
}

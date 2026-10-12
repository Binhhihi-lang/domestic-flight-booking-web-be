package com.example.domesticflightbookingwebbackend.entity;

import java.io.Serializable;
import java.util.Objects;

/** Khóa chính ghép của voucher_excluded_flights. */
public class VoucherExcludedFlightId implements Serializable {
    private Long voucher;
    private Long flight;

    public VoucherExcludedFlightId() {
    }

    public VoucherExcludedFlightId(Long voucher, Long flight) {
        this.voucher = voucher;
        this.flight = flight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VoucherExcludedFlightId that)) return false;
        return Objects.equals(voucher, that.voucher) && Objects.equals(flight, that.flight);
    }

    @Override
    public int hashCode() {
        return Objects.hash(voucher, flight);
    }
}

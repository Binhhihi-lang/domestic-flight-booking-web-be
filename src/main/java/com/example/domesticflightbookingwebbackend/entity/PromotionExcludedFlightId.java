package com.example.domesticflightbookingwebbackend.entity;

import java.io.Serializable;
import java.util.Objects;

/** Khóa chính ghép của promotion_excluded_flights. */
public class PromotionExcludedFlightId implements Serializable {
    private Long promotion;
    private Long flight;

    public PromotionExcludedFlightId() {
    }

    public PromotionExcludedFlightId(Long promotion, Long flight) {
        this.promotion = promotion;
        this.flight = flight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PromotionExcludedFlightId that)) return false;
        return Objects.equals(promotion, that.promotion) && Objects.equals(flight, that.flight);
    }

    @Override
    public int hashCode() {
        return Objects.hash(promotion, flight);
    }
}

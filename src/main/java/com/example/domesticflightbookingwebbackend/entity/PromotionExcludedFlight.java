package com.example.domesticflightbookingwebbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Chuyến bay bị loại trừ khỏi một khuyến mãi (loại trừ luôn thắng phạm vi). */
@Getter
@Setter
@Entity
@Table(name = "promotion_excluded_flights")
@IdClass(PromotionExcludedFlightId.class)
public class PromotionExcludedFlight {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotion promotion;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;
}

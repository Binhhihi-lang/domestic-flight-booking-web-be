package com.example.domesticflightbookingwebbackend.entity;

import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "flight_seats")
public class FlightSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_config_id", nullable = false)
    private SeatConfiguration seatConfiguration;

    @Column(name = "seat_code", nullable = false, length = 10)
    private String seatCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FlightSeatStatus status = FlightSeatStatus.AVAILABLE;

    @Column(name = "price_at_time", nullable = true, precision = 12, scale = 2)
    private BigDecimal priceAtTime;
}
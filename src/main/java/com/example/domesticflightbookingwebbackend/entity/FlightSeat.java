package com.example.domesticflightbookingwebbackend.entity;

import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Tồn ghế của một chuyến bay: một dòng là một ghế vật lý trên chuyến đó. */
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
    @JoinColumn(name = "aircraft_seat_id", nullable = false)
    private AircraftSeat aircraftSeat;

    @Column(name = "seat_code", nullable = false, length = 10)
    private String seatCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private FlightSeatStatus status = FlightSeatStatus.AVAILABLE;

    /** Phụ thu khi chọn ghế này (0 = không phụ thu). */
    @Column(name = "seat_surcharge", nullable = false, precision = 12, scale = 2)
    private BigDecimal seatSurcharge = BigDecimal.ZERO;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}

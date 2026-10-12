package com.example.domesticflightbookingwebbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Một ghế vật lý của máy bay (v0.4 đổi tên từ seat_configurations). */
@Getter
@Setter
@Entity
@Table(name = "aircraft_seats")
public class AircraftSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aircraft_id", nullable = false)
    private Aircraft aircraft;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_type_id", nullable = false)
    private SeatType seatType;

    @Column(name = "seat_row", nullable = false)
    private Integer seatRow;

    @Column(name = "seat_column", nullable = false, length = 2)
    private String seatColumn;
}

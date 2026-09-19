package com.example.domesticflightbookingwebbackend.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "e_tickets")
public class ETicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_flight_id", nullable = false)
    private BookingFlight bookingFlight;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "flight_seat_id", nullable = true)
    private FlightSeat flightSeat;

    @Column(name = "pnr_code", nullable = false, length = 20, unique = true)
    private String pnrCode;

    @Column(name = "ticket_code", nullable = false, length = 30, unique = true)
    private String ticketCode;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt = LocalDateTime.now();
}
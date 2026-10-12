package com.example.domesticflightbookingwebbackend.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Vé điện tử: vé của một hành khách trên một chặng bay. PNR lấy từ bookings.booking_code. */
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
    @JoinColumn(name = "booking_segment_id", nullable = false)
    private BookingSegment bookingSegment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_seat_id")
    private FlightSeat flightSeat;

    @Column(name = "ticket_code", nullable = false, length = 30, unique = true)
    private String ticketCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ETicketStatus status = ETicketStatus.ISSUED;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt = LocalDateTime.now();
}

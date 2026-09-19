package com.example.domesticflightbookingwebbackend.entity;

import java.time.LocalDate;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "passengers")
public class Passenger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 20)
    private PassengerGender gender;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "passenger_type", nullable = false, length = 20)
    private PassengerType passengerType;

    @Column(name = "passport_number", nullable = true, length = 50)
    private String passportNumber;

    @Column(name = "phone", nullable = true, length = 20)
    private String phone;

    @Column(name = "email", nullable = true, length = 255)
    private String email;
}
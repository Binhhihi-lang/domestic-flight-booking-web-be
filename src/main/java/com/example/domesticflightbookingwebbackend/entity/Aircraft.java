package com.example.domesticflightbookingwebbackend.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "aircrafts")
public class Aircraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aircraft_code", nullable = false, length = 30, unique = true)
    private String aircraftCode;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "manufacturer", nullable = true, length = 100)
    private String manufacturer;

    @Column(name = "seat_capacity", nullable = false)
    private Integer seatCapacity;
}
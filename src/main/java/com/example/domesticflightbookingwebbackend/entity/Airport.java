package com.example.domesticflightbookingwebbackend.entity;

import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "airports")
public class Airport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "airport_code", nullable = false, length = 10, unique = true)
    private String airportCode;

    @Column(name = "airport_name", nullable = false, length = 150)
    private String airportName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @Column(name = "latitude", nullable = true, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = true, precision = 9, scale = 6)
    private BigDecimal longitude;
}
package com.example.domesticflightbookingwebbackend.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "cities")
public class City {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "city_code", nullable = false, length = 10, unique = true)
    private String cityCode;

    @Column(name = "city_name", nullable = false, length = 100)
    private String cityName;

    @Column(name = "country", nullable = false, length = 100)
    private String country;
}
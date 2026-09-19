package com.example.domesticflightbookingwebbackend.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "permission_key", nullable = false, length = 100, unique = true)
    private String permissionKey;

    @Column(name = "description", nullable = true, length = 255)
    private String description;
}
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

    @Column(name = "description", length = 255)
    private String description;

    /** Nhóm module để lọc/sắp xếp catalog quyền (API v0.2 mục 2.4), ví dụ AUTH, AIRLINE. */
    @Column(name = "module", nullable = false, length = 30)
    private String module;
}

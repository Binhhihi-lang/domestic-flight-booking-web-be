package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
}
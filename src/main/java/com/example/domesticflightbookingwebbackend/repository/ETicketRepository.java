package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.ETicket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ETicketRepository extends JpaRepository<ETicket, Long> {
}
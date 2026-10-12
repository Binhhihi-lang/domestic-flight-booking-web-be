package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.VoucherExcludedFlight;
import com.example.domesticflightbookingwebbackend.entity.VoucherExcludedFlightId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherExcludedFlightRepository extends JpaRepository<VoucherExcludedFlight, VoucherExcludedFlightId> {
}

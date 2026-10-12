package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.VoucherExcludedFlight;
import com.example.domesticflightbookingwebbackend.entity.VoucherExcludedFlightId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VoucherExcludedFlightRepository
        extends JpaRepository<VoucherExcludedFlight, VoucherExcludedFlightId> {

    List<VoucherExcludedFlight> findByVoucherId(Long voucherId);
}

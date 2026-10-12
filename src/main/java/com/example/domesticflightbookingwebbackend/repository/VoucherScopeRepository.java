package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.VoucherScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VoucherScopeRepository extends JpaRepository<VoucherScope, Long> {

    List<VoucherScope> findByVoucherId(Long voucherId);
}

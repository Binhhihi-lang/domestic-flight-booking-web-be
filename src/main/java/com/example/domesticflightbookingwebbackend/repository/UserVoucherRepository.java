package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.UserVoucher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserVoucherRepository extends JpaRepository<UserVoucher, Long> {

    Page<UserVoucher> findByUserId(Long userId, Pageable pageable);

    Optional<UserVoucher> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndVoucherId(Long userId, Long voucherId);

    List<UserVoucher> findByUserIdAndVoucherId(Long userId, Long voucherId);
}

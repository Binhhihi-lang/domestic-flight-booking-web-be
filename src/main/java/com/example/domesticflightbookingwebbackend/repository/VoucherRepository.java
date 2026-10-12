package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long>, JpaSpecificationExecutor<Voucher> {

    Optional<Voucher> findByVoucherCodeAndIsDeletedFalse(String voucherCode);

    boolean existsByVoucherCodeAndIsDeletedFalse(String voucherCode);

    /**
     * Nhận voucher GLOBAL: tăng claimed_count CHỈ KHI còn lượt. Trả số dòng bị
     * ảnh hưởng — 1 mới được tạo user_vouchers, 0 nghĩa là hết lượt.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Voucher v SET v.claimedCount = v.claimedCount + 1 "
            + "WHERE v.id = :id AND (v.claimLimit IS NULL OR v.claimedCount < v.claimLimit)")
    int incrementClaimedCount(@Param("id") Long id);

    /** Kho voucher: GLOBAL, đang bật, chưa xóa mềm. */
    List<Voucher> findByDistributionTypeAndIsActiveTrueAndIsDeletedFalse(
            com.example.domesticflightbookingwebbackend.entity.VoucherDistributionType distributionType);
}

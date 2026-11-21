package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long>, JpaSpecificationExecutor<Promotion> {

    Optional<Promotion> findByPromotionCodeAndIsDeletedFalse(String promotionCode);

    boolean existsByPromotionCodeAndIsDeletedFalse(String promotionCode);

    /** Lấy toàn bộ chương trình khuyến mãi đang ACTIVE và nằm trong khoảng ngày hiệu lực. */
    List<Promotion> findByIsActiveTrueAndIsDeletedFalseAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            LocalDate startDateMax, LocalDate endDateMin);
}

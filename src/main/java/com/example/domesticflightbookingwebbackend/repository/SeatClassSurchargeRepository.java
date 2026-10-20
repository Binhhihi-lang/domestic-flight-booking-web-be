package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.SeatClass;
import com.example.domesticflightbookingwebbackend.entity.SeatClassSurcharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SeatClassSurchargeRepository
        extends JpaRepository<SeatClassSurcharge, Long>, JpaSpecificationExecutor<SeatClassSurcharge> {

    boolean existsByFromClassAndToClassAndIsDeletedFalse(SeatClass fromClass, SeatClass toClass);

    /** Khi SỬA: kiểm trùng cặp nhưng bỏ qua chính bản ghi đang sửa. */
    boolean existsByFromClassAndToClassAndIsDeletedFalseAndIdNot(
            SeatClass fromClass, SeatClass toClass, Long id);
}

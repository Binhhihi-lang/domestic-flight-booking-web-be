package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.FareClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FareClassRepository extends JpaRepository<FareClass, Long>, JpaSpecificationExecutor<FareClass> {

    /** Mã hạng vé (đã gồm tiền tố hãng) duy nhất toàn hệ thống — V18. */
    boolean existsByFareClassCodeAndIsDeletedFalse(String fareClassCode);

    /** Nạp kèm hãng trong cùng câu SQL — danh sách hiện cột hãng, tránh N+1. */
    @Override
    @EntityGraph(attributePaths = "airline")
    Page<FareClass> findAll(Specification<FareClass> spec, Pageable pageable);
}

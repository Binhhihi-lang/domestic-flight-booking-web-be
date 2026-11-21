package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.BaggageOption;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BaggageOptionRepository extends JpaRepository<BaggageOption, Long>,
        JpaSpecificationExecutor<BaggageOption> {

    /** Tên gói không trùng trong cùng một hãng (không phân biệt hoa thường). */
    boolean existsByAirlineIdAndNameIgnoreCaseAndIsDeletedFalse(Long airlineId, String name);

    boolean existsByAirlineIdAndNameIgnoreCaseAndIsDeletedFalseAndIdNot(Long airlineId, String name, Long id);

    /** Nạp kèm hãng trong cùng câu SQL, tránh N+1. */
    @Override
    @EntityGraph(attributePaths = "airline")
    Page<BaggageOption> findAll(Specification<BaggageOption> spec, Pageable pageable);
}

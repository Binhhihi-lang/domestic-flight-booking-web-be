package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.FareRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FareRuleRepository extends JpaRepository<FareRule, Long>, JpaSpecificationExecutor<FareRule> {

    /**
     * Version lớn nhất của một hạng vé, TÍNH CẢ dòng đã xóa mềm — vì
     * UNIQUE (fare_class_id, version) ở V6 là ràng buộc toàn phần.
     */
    @Query("select coalesce(max(r.version), 0) from FareRule r where r.fareClass.id = :fareClassId")
    int findMaxVersion(@Param("fareClassId") Long fareClassId);

    /** Nạp kèm hạng vé + hãng trong cùng câu SQL, tránh N+1. */
    @Override
    @EntityGraph(attributePaths = {"fareClass", "fareClass.airline"})
    Page<FareRule> findAll(Specification<FareRule> spec, Pageable pageable);
}

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

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    /** Lấy quy định hoàn vé đang ACTIVE mới nhất của một hạng vé (version cao nhất, effective_from <= now). */
    @Query("""
            SELECT r FROM FareRule r
            WHERE r.fareClass.id = :fareClassId
              AND r.isDeleted = false
              AND r.isActive = true
              AND r.effectiveFrom <= :now
            ORDER BY r.version DESC
            """)
    List<FareRule> findActiveRulesByFareClassId(
            @Param("fareClassId") Long fareClassId,
            @Param("now") LocalDateTime now);

    /** Lấy các quy định hoàn vé đang ACTIVE cho một tập hạng vé (dùng khi search chuyến bay, chống N+1). */
    @Query("""
            SELECT r FROM FareRule r
            WHERE r.fareClass.id IN :fareClassIds
              AND r.isDeleted = false
              AND r.isActive = true
              AND r.effectiveFrom <= :now
            ORDER BY r.fareClass.id ASC, r.version DESC
            """)
    List<FareRule> findActiveRulesByFareClassIds(
            @Param("fareClassIds") Collection<Long> fareClassIds,
            @Param("now") LocalDateTime now);

    /** Lấy toàn bộ quy định hoàn vé đang hiệu lực của các hạng vé ACTIVE cho API công khai (JOIN FETCH chống N+1). */
    @Query("""
            SELECT r FROM FareRule r
            JOIN FETCH r.fareClass fc
            JOIN FETCH fc.airline a
            WHERE r.isDeleted = false
              AND r.isActive = true
              AND r.effectiveFrom <= :now
              AND fc.isDeleted = false
              AND fc.isActive = true
              AND a.isDeleted = false
              AND a.isActive = true
              AND (:airlineId IS NULL OR a.id = :airlineId)
            ORDER BY a.airlineCode ASC, fc.fareClassCode ASC, r.version DESC
            """)
    List<FareRule> findAllActivePublicRules(
            @Param("airlineId") Long airlineId,
            @Param("now") LocalDateTime now);
}

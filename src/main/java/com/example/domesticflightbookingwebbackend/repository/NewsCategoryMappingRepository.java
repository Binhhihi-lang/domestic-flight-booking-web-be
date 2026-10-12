package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.NewsCategoryMapping;
import com.example.domesticflightbookingwebbackend.entity.NewsCategoryMappingId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsCategoryMappingRepository extends JpaRepository<NewsCategoryMapping, NewsCategoryMappingId> {

    List<NewsCategoryMapping> findByNewsId(Long newsId);

    /** Đếm bài viết thuộc một chuyên mục — dùng khi xóa chuyên mục. */
    long countByCategoryId(Long categoryId);

    void deleteByNewsId(Long newsId);
}

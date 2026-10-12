package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.NewsCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface NewsCategoryRepository extends JpaRepository<NewsCategory, Long>, JpaSpecificationExecutor<NewsCategory> {

    Optional<NewsCategory> findByNameAndIsDeletedFalse(String name);

    boolean existsByNameAndIsDeletedFalse(String name);
}

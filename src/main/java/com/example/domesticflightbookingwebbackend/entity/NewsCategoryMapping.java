package com.example.domesticflightbookingwebbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Gán bài viết vào chuyên mục (N—N). */
@Getter
@Setter
@Entity
@Table(name = "news_category_mapping")
@IdClass(NewsCategoryMappingId.class)
public class NewsCategoryMapping {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "news_id", nullable = false)
    private NewsItem news;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private NewsCategory category;
}

package com.example.domesticflightbookingwebbackend.entity;

import java.io.Serializable;
import java.util.Objects;

/** Khóa chính ghép của news_category_mapping. */
public class NewsCategoryMappingId implements Serializable {
    private Long news;
    private Long category;

    public NewsCategoryMappingId() {
    }

    public NewsCategoryMappingId(Long news, Long category) {
        this.news = news;
        this.category = category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NewsCategoryMappingId that)) return false;
        return Objects.equals(news, that.news) && Objects.equals(category, that.category);
    }

    @Override
    public int hashCode() {
        return Objects.hash(news, category);
    }
}

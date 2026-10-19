package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.entity.NewsItem;
import com.example.domesticflightbookingwebbackend.entity.NewsStatus;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.repository.NewsCategoryMappingRepository;
import com.example.domesticflightbookingwebbackend.repository.NewsCategoryRepository;
import com.example.domesticflightbookingwebbackend.repository.NewsItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test chặn đọc bài DRAFT ở vùng khách (lỗ hổng 4.9-A API v0.4).
 * Vùng admin đọc mọi trạng thái qua {@code getById} của base.
 */
class NewsServiceTest {

    private NewsItemRepository repository;
    private NewsCategoryMappingRepository mappingRepository;
    private NewsService service;

    @BeforeEach
    void setUp() {
        repository = mock(NewsItemRepository.class);
        mappingRepository = mock(NewsCategoryMappingRepository.class);
        service = new NewsService(repository, mock(NewsCategoryRepository.class), mappingRepository);
    }

    private void givenNews(NewsStatus status) {
        NewsItem item = new NewsItem();
        item.setId(5L);
        item.setTitle("Bài viết");
        item.setContent("<p>Nội dung</p>");
        item.setStatus(status);
        item.setIsDeleted(false);

        when(repository.findById(5L)).thenReturn(Optional.of(item));
        when(mappingRepository.findByNewsId(5L)).thenReturn(List.of());
    }

    @Test
    void khachDocBaiDraft_nem1004() {
        givenNews(NewsStatus.DRAFT);

        assertThatThrownBy(() -> service.getPublicById(5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void khachDocBaiUnpublished_nem1004() {
        givenNews(NewsStatus.UNPUBLISHED);

        assertThatThrownBy(() -> service.getPublicById(5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void khachDocBaiPublished_traBinhThuong() {
        givenNews(NewsStatus.PUBLISHED);

        assertThat(service.getPublicById(5L).title()).isEqualTo("Bài viết");
    }

    @Test
    void adminDocBaiDraft_traBinhThuong() {
        givenNews(NewsStatus.DRAFT);

        assertThat(service.getById(5L).status()).isEqualTo("DRAFT");
    }
}

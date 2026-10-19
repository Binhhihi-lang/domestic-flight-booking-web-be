package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.HtmlSanitizer;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.dto.request.news.NewsRequest;
import com.example.domesticflightbookingwebbackend.dto.response.news.NewsResponse;
import com.example.domesticflightbookingwebbackend.entity.NewsCategory;
import com.example.domesticflightbookingwebbackend.entity.NewsCategoryMapping;
import com.example.domesticflightbookingwebbackend.entity.NewsItem;
import com.example.domesticflightbookingwebbackend.entity.NewsStatus;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.repository.NewsCategoryMappingRepository;
import com.example.domesticflightbookingwebbackend.repository.NewsCategoryRepository;
import com.example.domesticflightbookingwebbackend.repository.NewsItemRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** News — bài viết tin tức (mục 2.9 API v0.2). */
@Slf4j
@Service
public class NewsService extends BaseCrudService<NewsItem, Long, NewsRequest, NewsResponse> {

    private static final String[] SORT_WHITELIST = {"published_at", "title"};

    private final NewsItemRepository newsRepository;
    private final NewsCategoryRepository categoryRepository;
    private final NewsCategoryMappingRepository mappingRepository;

    public NewsService(NewsItemRepository repository,
                       NewsCategoryRepository categoryRepository,
                       NewsCategoryMappingRepository mappingRepository) {
        super(repository);
        this.newsRepository = repository;
        this.categoryRepository = categoryRepository;
        this.mappingRepository = mappingRepository;
    }

    @Override
    protected String resourceName() {
        return "Bài viết";
    }

    @Override
    protected NewsResponse toResponse(NewsItem entity) {
        return toResponseWithCategories(entity);
    }

    @Override
    protected NewsItem createEntity(NewsRequest request) {
        NewsItem item = new NewsItem();
        item.setTitle(request.title().trim());
        item.setContent(HtmlSanitizer.sanitize(request.content()));   // chống XSS
        item.setThumbnailUrl(request.thumbnailUrl());
        item.setStatus(NewsStatus.DRAFT);   // bài mới luôn là DRAFT
        return item;
    }

    @Override
    protected void updateEntity(NewsItem entity, NewsRequest request) {
        entity.setTitle(request.title().trim());
        entity.setContent(HtmlSanitizer.sanitize(request.content()));
        entity.setThumbnailUrl(request.thumbnailUrl());
        entity.setUpdatedAt(LocalDateTime.now());
    }

    @Override
    @Transactional
    public NewsResponse create(NewsRequest request) {
        NewsItem saved = newsRepository.save(createEntity(request));
        replaceCategories(saved, request.categoryIds());
        log.info("Tạo bài viết id={} thành công (DRAFT)", saved.getId());
        return toResponseWithCategories(saved);
    }

    @Override
    @Transactional
    public NewsResponse update(Long id, NewsRequest request) {
        NewsItem item = findOrThrow(id);
        updateEntity(item, request);
        newsRepository.save(item);
        replaceCategories(item, request.categoryIds());
        log.info("Cập nhật bài viết id={} thành công", id);
        return toResponseWithCategories(item);
    }

    /**
     * Chi tiết cho khách: chỉ trả bài PUBLISHED. Bài DRAFT/UNPUBLISHED trả 1004
     * (không phải 1003) để không xác nhận id tồn tại — mục 4.9-A API v0.4.
     * Admin đọc bản nháp qua {@link #getById} ở vùng /admin.
     */
    @Transactional(readOnly = true)
    public NewsResponse getPublicById(Long id) {
        NewsItem item = findOrThrow(id);
        if (item.getStatus() != NewsStatus.PUBLISHED) {
            log.warn("Chặn đọc bài viết id={} ở trạng thái {}", id, item.getStatus());
            throw new ResourceNotFoundException(resourceName() + " không tồn tại");
        }
        return toResponseWithCategories(item);
    }

    /** Bulk cho News: nhận PUBLISHED | UNPUBLISHED (không nhận DRAFT — về nháp là unpublish). */
    @Override
    protected void applyBulkStatus(Long id, String status) {
        if ("PUBLISHED".equalsIgnoreCase(status)) {
            publish(id);
        } else if ("UNPUBLISHED".equalsIgnoreCase(status)) {
            unpublish(id);
        } else {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Trạng thái chỉ nhận PUBLISHED hoặc UNPUBLISHED");
        }
    }

    /** Publish: gán PUBLISHED + published_at. Phải có ≥1 chuyên mục (BR-NEWS-03). */
    @Transactional
    public NewsResponse publish(Long id) {
        NewsItem item = findOrThrow(id);
        if (mappingRepository.findByNewsId(id).isEmpty()) {
            log.warn("Publish bài viết id={} thất bại: chưa có chuyên mục", id);
            throw new BusinessException("Bài viết phải có ít nhất một chuyên mục trước khi publish");
        }
        item.setStatus(NewsStatus.PUBLISHED);
        item.setPublishedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        newsRepository.save(item);
        log.info("Publish bài viết id={} thành công", id);
        return toResponseWithCategories(item);
    }

    /** Unpublish: về DRAFT, GIỮ published_at cũ (NEWS-05). */
    @Transactional
    public NewsResponse unpublish(Long id) {
        NewsItem item = findOrThrow(id);
        item.setStatus(NewsStatus.DRAFT);
        item.setUpdatedAt(LocalDateTime.now());
        newsRepository.save(item);
        log.info("Unpublish bài viết id={}", id);
        return toResponseWithCategories(item);
    }

    private void replaceCategories(NewsItem news, List<Long> categoryIds) {
        mappingRepository.deleteByNewsId(news.getId());
        if (categoryIds == null) {
            return;
        }
        for (Long categoryId : categoryIds) {
            NewsCategory category = categoryRepository.findById(categoryId)
                    .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                    .orElseThrow(() -> new ResourceNotFoundException("Chuyên mục không tồn tại: " + categoryId));
            NewsCategoryMapping mapping = new NewsCategoryMapping();
            mapping.setNews(news);
            mapping.setCategory(category);
            mappingRepository.save(mapping);
        }
    }

    private NewsResponse toResponseWithCategories(NewsItem item) {
        List<NewsResponse.CategoryBrief> categories = mappingRepository.findByNewsId(item.getId()).stream()
                .map(m -> new NewsResponse.CategoryBrief(m.getCategory().getId(), m.getCategory().getName()))
                .toList();
        return new NewsResponse(
                item.getId(), item.getTitle(), item.getContent(), item.getThumbnailUrl(),
                item.getStatus().name(), item.getPublishedAt(), item.getCreatedAt(), categories);
    }

    @Override
    protected void softDelete(NewsItem entity) {
        entity.setIsDeleted(true);
        entity.setDeletedAt(LocalDateTime.now());
    }

    @Override
    protected void setActive(NewsItem entity, boolean active) {
        // News dùng status (DRAFT/PUBLISHED/UNPUBLISHED), không dùng is_active.
        // Bật lần đầu phải gán published_at, nếu không bài hiện ra với ngày đăng null.
        entity.setStatus(active ? NewsStatus.PUBLISHED : NewsStatus.UNPUBLISHED);
        if (active && entity.getPublishedAt() == null) {
            entity.setPublishedAt(LocalDateTime.now());
        }
        entity.setUpdatedAt(LocalDateTime.now());
    }

    @Override
    protected Page<NewsItem> search(ListQuery query, Map<String, String> filters) {
        Pageable pageable = PageMapper.toPageable(query, "publishedAt", SORT_WHITELIST);
        return newsRepository.findAll(buildSpec(query, filters), pageable);
    }

    /** Vùng khách: chỉ bài PUBLISHED, không nhận tham số status (mục 2.11 API v0.4). */
    @Transactional(readOnly = true)
    public PageData<NewsResponse> listPublic(ListQuery query, String categoryId) {
        Pageable pageable = PageMapper.toPageable(query, "publishedAt", SORT_WHITELIST);
        Map<String, String> filters = new HashMap<>();
        filters.put("status", NewsStatus.PUBLISHED.name());   // ép cứng, không cho client ghi đè
        if (categoryId != null && !categoryId.isBlank()) {
            filters.put("category_id", categoryId);
        }
        return PageMapper.toPageData(newsRepository.findAll(buildSpec(query, filters), pageable),
                this::toResponse);
    }

    /** Khách chỉ thấy PUBLISHED; ADMIN/STAFF truyền status để xem DRAFT (BR-LIST-04). */
    private Specification<NewsItem> buildSpec(ListQuery query, Map<String, String> filters) {        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));   // BR-LIST-04

            String status = filters.get("status");
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), NewsStatus.valueOf(status)));
            } else {
                predicates.add(cb.equal(root.get("status"), NewsStatus.PUBLISHED));
            }

            String categoryId = filters.get("category_id");
            if (categoryId != null) {
                var sub = cq.subquery(Long.class);
                var mapping = sub.from(NewsCategoryMapping.class);
                sub.select(mapping.get("news").get("id"))
                        .where(cb.equal(mapping.get("category").get("id"), Long.valueOf(categoryId)));
                predicates.add(root.get("id").in(sub));
            }

            String keyword = query.getKeywordOrNull();
            if (keyword != null) {
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

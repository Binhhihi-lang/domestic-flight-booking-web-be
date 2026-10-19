package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.dto.request.news.NewsCategoryRequest;
import com.example.domesticflightbookingwebbackend.dto.response.news.NewsCategoryResponse;
import com.example.domesticflightbookingwebbackend.entity.NewsCategory;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.DuplicateResourceException;
import com.example.domesticflightbookingwebbackend.repository.NewsCategoryMappingRepository;
import com.example.domesticflightbookingwebbackend.repository.NewsCategoryRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** NewsCategory — KHÔNG public (khác News), mục 2.9 API v0.2. */
@Slf4j
@Service
public class NewsCategoryService extends BaseCrudService<NewsCategory, Long, NewsCategoryRequest, NewsCategoryResponse> {

    private static final String[] SORT_WHITELIST = {"name"};

    private final NewsCategoryRepository categoryRepository;
    private final NewsCategoryMappingRepository mappingRepository;

    public NewsCategoryService(NewsCategoryRepository repository,
                               NewsCategoryMappingRepository mappingRepository) {
        super(repository);
        this.categoryRepository = repository;
        this.mappingRepository = mappingRepository;
    }

    @Override
    protected String resourceName() {
        return "Chuyên mục";
    }

    @Override
    protected NewsCategoryResponse toResponse(NewsCategory entity) {
        return new NewsCategoryResponse(
                entity.getId(), entity.getName(), entity.getDescription(),
                Boolean.TRUE.equals(entity.getIsActive()) ? "ACTIVE" : "INACTIVE");
    }

    @Override
    protected NewsCategory createEntity(NewsCategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameAndIsDeletedFalse(name)) {
            log.warn("Tạo chuyên mục thất bại: tên {} đã tồn tại", name);
            throw new DuplicateResourceException("Tên chuyên mục đã tồn tại: " + name);
        }
        NewsCategory category = new NewsCategory();
        category.setName(name);
        applyRequest(category, request);
        return category;
    }

    @Override
    protected void updateEntity(NewsCategory entity, NewsCategoryRequest request) {
        entity.setName(request.name().trim());
        applyRequest(entity, request);
    }

    private void applyRequest(NewsCategory category, NewsCategoryRequest request) {
        category.setDescription(request.description());
        category.setIsActive(!"INACTIVE".equalsIgnoreCase(request.status()));
    }

    /** Chuyên mục còn bài viết tham chiếu → từ chối xóa (mục 2.9). */
    @Override
    @Transactional
    public void delete(Long id) {
        NewsCategory category = findOrThrow(id);
        if (mappingRepository.countByCategoryId(id) > 0) {
            log.warn("Xóa chuyên mục id={} bị từ chối: còn bài viết tham chiếu", id);
            throw new BusinessException("Chuyên mục còn bài viết — không thể xóa");
        }
        super.delete(id);
    }

    @Override
    protected void softDelete(NewsCategory entity) {
        entity.setIsDeleted(true);
        entity.setIsActive(false);
        entity.setDeletedAt(LocalDateTime.now());
    }

    @Override
    protected void setActive(NewsCategory entity, boolean active) {
        entity.setIsActive(active);
    }

    @Override
    protected Page<NewsCategory> search(ListQuery query, Map<String, String> filters) {
        Pageable pageable = PageMapper.toPageable(query, "name", SORT_WHITELIST);
        return categoryRepository.findAll(buildSpec(query), pageable);
    }

    /**
     * Danh mục cho khách: trả mảng trần, chỉ id + name, chỉ đang hoạt động
     * (mục 2.9 API v0.4). Không lộ description/status.
     */
    @Transactional(readOnly = true)
    public List<NewsCategoryBrief> listPublic() {
        return categoryRepository.findAll(
                        (root, cq, cb) -> cb.and(
                                cb.isFalse(root.get("isDeleted")),
                                cb.isTrue(root.get("isActive"))))
                .stream()
                .map(c -> new NewsCategoryBrief(c.getId(), c.getName()))
                .toList();
    }

    /** Dòng danh mục rút gọn cho vùng /public/news-categories. */
    public record NewsCategoryBrief(Long id, String name) {
    }

    private Specification<NewsCategory> buildSpec(ListQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));   // BR-LIST-04

            String keyword = query.getKeywordOrNull();
            if (keyword != null) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + keyword.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

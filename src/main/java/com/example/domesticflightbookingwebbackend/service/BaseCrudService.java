package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * CRUD chung cho master data. Module con khai DTO/mapper/filter + quy tắc riêng.
 *
 * @param <T> entity, <ID> khóa chính, <Req> request DTO, <Resp> response DTO
 */
@Slf4j
public abstract class BaseCrudService<T, ID, Req, Resp> {

    protected final JpaRepository<T, ID> repository;

    protected BaseCrudService(JpaRepository<T, ID> repository) {
        this.repository = repository;
    }

    /** Tên tài nguyên cho message lỗi 1004. */
    protected abstract String resourceName();

    protected abstract Resp toResponse(T entity);

    protected abstract T createEntity(Req request);

    /** Áp request lên entity. Không đổi cột mã nghiệp vụ. */
    protected abstract void updateEntity(T entity, Req request);

    /** Xóa mềm: is_deleted = true + deleted_at. */
    protected abstract void softDelete(T entity);

    protected abstract void setActive(T entity, boolean active);

    /** Truy vấn danh sách. filters = filter riêng của module (rỗng đã bị loại). */
    protected abstract Page<T> search(ListQuery query, Map<String, String> filters);

    @Transactional(readOnly = true)
    public PageData<Resp> list(ListQuery query, Map<String, String> filters) {
        PageData<Resp> page = PageMapper.toPageData(search(query, filters), this::toResponse);
        log.debug("List {} -> {} items, filters={}", resourceName(), page.items().size(), filters);
        return page;
    }

    @Transactional(readOnly = true)
    public Resp getById(ID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public Resp create(Req request) {
        T saved = repository.save(createEntity(request));
        log.info("Tạo {} thành công", resourceName());
        return toResponse(saved);
    }

    @Transactional
    public Resp update(ID id, Req request) {
        T entity = findOrThrow(id);
        updateEntity(entity, request);
        T saved = repository.save(entity);
        log.info("Cập nhật {} id={} thành công", resourceName(), id);
        return toResponse(saved);
    }

    @Transactional
    public Resp toggleActive(ID id, boolean active) {
        T entity = findOrThrow(id);
        setActive(entity, active);
        T saved = repository.save(entity);
        log.info("Đổi trạng thái {} id={} -> {}", resourceName(), id, active ? "ACTIVE" : "INACTIVE");
        return toResponse(saved);
    }

    @Transactional
    public void delete(ID id) {
        T entity = findOrThrow(id);
        softDelete(entity);
        repository.save(entity);
        log.info("Xóa mềm {} id={}", resourceName(), id);
    }

    protected T findOrThrow(ID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(resourceName() + " không tồn tại"));
    }
}

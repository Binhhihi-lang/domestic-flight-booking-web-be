package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.dto.response.common.BulkResult;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CRUD chung cho master data. Module con khai DTO/mapper/filter + quy tắc riêng.
 *
 * @param <T> entity, <ID> khóa chính, <Req> request DTO, <Resp> response DTO
 */
@Slf4j
public abstract class BaseCrudService<T, ID, Req, Resp> {

    protected final JpaRepository<T, ID> repository;

    /** Chỉ cần cho thao tác hàng loạt; null khi service được dựng tay trong unit test. */
    private PlatformTransactionManager transactionManager;

    protected BaseCrudService(JpaRepository<T, ID> repository) {
        this.repository = repository;
    }

    @Autowired(required = false)
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
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

    // ================== Thao tác hàng loạt (mục 2.10 API v0.4) ==================

    /**
     * Bật/tắt trạng thái nhiều bản ghi. Trả kết quả TỪNG PHẦN: bản ghi vi phạm
     * ràng buộc vào {@code failed}, không chặn cả lô.
     *
     * <p>Mỗi bản ghi chạy trong transaction RIÊNG (TransactionTemplate). Nếu bọc
     * một transaction chung, exception của bản ghi lỗi sẽ đánh dấu rollback-only
     * và xóa luôn các bản ghi đã thành công.
     */
    public BulkResult bulkToggle(List<ID> ids, String status) {
        List<Long> succeeded = new ArrayList<>();
        List<BulkResult.BulkFailure> failed = new ArrayList<>();
        for (ID id : ids) {
            try {
                perRecord(() -> applyBulkStatus(id, status));
                succeeded.add(toLong(id));
            } catch (AppException e) {
                failed.add(new BulkResult.BulkFailure(toLong(id), e.getErrorCode().getCode(), e.getMessage()));
            }
        }
        log.info("Bulk trạng thái {} -> {}: {} thành công, {} thất bại",
                resourceName(), status, succeeded.size(), failed.size());
        return new BulkResult(succeeded, failed);
    }

    /** Xóa mềm nhiều bản ghi — cùng quy tắc trả kết quả từng phần. */
    public BulkResult bulkDelete(List<ID> ids) {
        List<Long> succeeded = new ArrayList<>();
        List<BulkResult.BulkFailure> failed = new ArrayList<>();
        for (ID id : ids) {
            try {
                perRecord(() -> delete(id));
                succeeded.add(toLong(id));
            } catch (AppException e) {
                failed.add(new BulkResult.BulkFailure(toLong(id), e.getErrorCode().getCode(), e.getMessage()));
            }
        }
        log.info("Bulk xóa {}: {} thành công, {} thất bại",
                resourceName(), succeeded.size(), failed.size());
        return new BulkResult(succeeded, failed);
    }

    /** Chạy một thao tác trong transaction riêng cho đúng một bản ghi. */
    private void perRecord(Runnable action) {
        if (transactionManager == null) {   // unit test dựng service trực tiếp
            action.run();
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    /**
     * Áp một trạng thái cho một bản ghi trong bulk. Mặc định ACTIVE/INACTIVE;
     * module có trạng thái riêng (News) override.
     */
    protected void applyBulkStatus(ID id, String status) {
        if (!"ACTIVE".equalsIgnoreCase(status) && !"INACTIVE".equalsIgnoreCase(status)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Trạng thái chỉ nhận ACTIVE hoặc INACTIVE");
        }
        toggleActive(id, "ACTIVE".equalsIgnoreCase(status));
    }

    /** Id là Long ở mọi module hiện có; giữ hàm riêng để không phụ thuộc kiểu ID. */
    @SuppressWarnings("unchecked")
    private Long toLong(ID id) {
        return (Long) id;
    }
}

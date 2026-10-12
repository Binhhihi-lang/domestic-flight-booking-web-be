package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lớp CRUD chung cho các module master data (Airline, City, Airport, Aircraft,
 * SeatType, Promotion, Voucher, News...).
 *
 * <p>Vì sao cần lớp này: 7 module master data có cùng khuôn list → create →
 * edit → toggle → soft-delete. Viết một lần rồi nhân bản; module con chỉ khai
 * DTO, mapper và quy tắc nghiệp vụ riêng.
 *
 * <p>Module con chỉ cần implement 6 hook bên dưới; phần còn lại (transaction,
 * ném lỗi 1004, phân trang) đã xử lý sẵn ở đây.
 *
 * @param <T>    kiểu entity
 * @param <ID>   kiểu khóa chính (Long)
 * @param <Req>  kiểu DTO request (create/update)
 * @param <Resp> kiểu DTO response
 */
public abstract class BaseCrudService<T, ID, Req, Resp> {

    protected final JpaRepository<T, ID> repository;

    protected BaseCrudService(JpaRepository<T, ID> repository) {
        this.repository = repository;
    }

    // ================= Hook cho module con =================

    /** Tên hiển thị của tài nguyên, dùng trong message lỗi 1004. */
    protected abstract String resourceName();

    /** Entity → DTO response. */
    protected abstract Resp toResponse(T entity);

    /** Dựng entity mới từ request (chưa lưu). */
    protected abstract T createEntity(Req request);

    /** Áp request lên entity đang có (update). */
    protected abstract void updateEntity(T entity, Req request);

    /** Xóa mềm: đặt is_deleted = true + deleted_at, KHÔNG xóa bản ghi. */
    protected abstract void softDelete(T entity);

    /** Bật/tắt sử dụng: đặt is_active. */
    protected abstract void setActive(T entity, boolean active);

    /**
     * Truy vấn danh sách có lọc/sắp xếp/phân trang.
     * Module con tự dựng Specification + dùng {@link PageMapper#toPageable}.
     */
    protected abstract Page<T> search(ListQuery query);

    // ================= Hành vi dùng chung =================

    @Transactional(readOnly = true)
    public PageData<Resp> list(ListQuery query) {
        return PageMapper.toPageData(search(query), this::toResponse);
    }

    @Transactional(readOnly = true)
    public Resp getById(ID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public Resp create(Req request) {
        T entity = createEntity(request);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public Resp update(ID id, Req request) {
        T entity = findOrThrow(id);
        updateEntity(entity, request);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public Resp toggleActive(ID id, boolean active) {
        T entity = findOrThrow(id);
        setActive(entity, active);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(ID id) {
        T entity = findOrThrow(id);
        softDelete(entity);
        repository.save(entity);
    }

    /** Tìm entity hoặc ném 1004. Module con gọi lại khi cần lấy entity thô. */
    protected T findOrThrow(ID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(resourceName() + " không tồn tại"));
    }
}

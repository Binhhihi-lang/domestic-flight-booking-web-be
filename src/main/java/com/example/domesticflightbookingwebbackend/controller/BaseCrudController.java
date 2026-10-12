package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.service.BaseCrudService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Lớp REST chung cho các module master data — 5 endpoint theo cùng khuôn
 * (mục 2.5 — API Design v0.2):
 *
 * <pre>
 *   GET    /            danh sách (phân trang, lọc, sắp xếp)
 *   GET    /{id}        chi tiết
 *   POST   /            tạo mới          → 201
 *   PUT    /{id}        cập nhật + toggle is_active
 *   DELETE /{id}        xóa mềm          → 200, data: null
 * </pre>
 *
 * <p>Module con khai {@code @RequestMapping("/api/v1/<resource>")} và tiêm
 * service tương ứng. Phân quyền bằng {@code @PreAuthorize} đặt ở method của
 * lớp con (mỗi module một permission prefix khác nhau).
 */
public abstract class BaseCrudController<T, ID, Req, Resp> {

    protected final BaseCrudService<T, ID, Req, Resp> service;

    protected BaseCrudController(BaseCrudService<T, ID, Req, Resp> service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageData<Resp>> list(ListQuery query) {
        return ApiResponse.success(service.list(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<Resp> getById(@PathVariable ID id) {
        return ApiResponse.success(service.getById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Resp>> create(@Valid @RequestBody Req request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Resp> update(@PathVariable ID id, @Valid @RequestBody Req request) {
        return ApiResponse.success(service.update(id, request));
    }

    /** Toggle trạng thái dùng: PUT /{id}/active?value=false */
    @PutMapping("/{id}/active")
    public ApiResponse<Resp> toggleActive(@PathVariable ID id, @RequestParam boolean value) {
        return ApiResponse.success(service.toggleActive(id, value));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable ID id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}

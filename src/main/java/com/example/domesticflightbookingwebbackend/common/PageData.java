package com.example.domesticflightbookingwebbackend.common;

import java.util.List;

/**
 * Payload của mọi endpoint danh sách (mục 1.6 — API Design v0.2).
 * Serialize ra JSON snake_case: {@code {"items": [...], "pagination": {...}}}
 * nhờ cấu hình Jackson SNAKE_CASE toàn cục.
 */
public record PageData<T>(List<T> items, Pagination pagination) {

    /** Khối phân trang. {@code page} bắt đầu từ 1 (Spring Page là 0-based). */
    public record Pagination(int page, int size, long totalItems, int totalPages) {
    }
}

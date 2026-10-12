package com.example.domesticflightbookingwebbackend.common;

import lombok.Getter;
import lombok.Setter;

/**
 * Query params chuẩn cho mọi endpoint danh sách (mục 1.6 — API Design v0.2).
 * Spring bind tự động từ request params: {@code ?page=1&size=20&keyword=vn&sort=airline_name,asc}
 *
 * <p>Chỉ giữ 4 param chung. Filter riêng của từng module (status, city_id, ...)
 * khai báo thêm ở method của controller đó.
 */
@Getter
@Setter
public class ListQuery {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private Integer page;
    private Integer size;
    private String keyword;

    /** Dạng {@code field,direction}, ví dụ {@code airline_name,asc}. */
    private String sort;

    /** Trang hiện tại, đảm bảo ≥ 1. */
    public int getPageOrDefault() {
        return (page == null || page < 1) ? DEFAULT_PAGE : page;
    }

    /** Số bản ghi/trang, kẹp trong [1, 100]. */
    public int getSizeOrDefault() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    public String getKeywordOrNull() {
        return (keyword == null || keyword.isBlank()) ? null : keyword.trim();
    }

    /** Trả {@code true} nếu sort hợp lệ và field nằm trong whitelist của module. */
    public boolean isSortAllowed(String... allowedFields) {
        String field = getSortField();
        if (field == null) {
            return false;
        }
        for (String allowed : allowedFields) {
            if (allowed.equals(field)) {
                return true;
            }
        }
        return false;
    }

    /** Field sắp xếp (phần trước dấu phẩy), hoặc {@code null} nếu không hợp lệ. */
    public String getSortField() {
        if (sort == null || sort.isBlank()) {
            return null;
        }
        String[] parts = sort.split(",");
        if (parts.length != 2 || parts[0].isBlank()) {
            return null;
        }
        return parts[0].trim();
    }

    /** Hướng sắp xếp: {@code asc} hoặc {@code desc}; mặc định {@code asc}. */
    public String getSortDirection() {
        if (sort == null || sort.isBlank()) {
            return "asc";
        }
        String[] parts = sort.split(",");
        if (parts.length != 2) {
            return "asc";
        }
        String dir = parts[1].trim().toLowerCase();
        return "desc".equals(dir) ? "desc" : "asc";
    }
}

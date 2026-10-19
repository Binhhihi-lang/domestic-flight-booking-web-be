package com.example.domesticflightbookingwebbackend.dto.response.common;

import java.util.List;

/**
 * Kết quả TỪNG PHẦN của thao tác hàng loạt (mục 2.10 API v0.4).
 * Bản ghi vi phạm ràng buộc vào {@code failed} kèm mã lỗi, không chặn cả lô.
 */
public record BulkResult(List<Long> succeeded, List<BulkFailure> failed) {

    public record BulkFailure(Long id, int code, String message) {
    }
}

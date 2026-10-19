package com.example.domesticflightbookingwebbackend.dto.request.common;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body của PATCH/DELETE /api/admin/{resource}/bulk (mục 2.10 API v0.4).
 * Tối đa 100 id mỗi lần gọi — vượt ngưỡng trả 1001.
 */
public record BulkRequest(

        @NotEmpty(message = "Danh sách id không được để trống")
        @Size(max = 100, message = "Tối đa 100 id mỗi lần gọi")
        List<Long> ids,

        /** Chỉ dùng cho PATCH: ACTIVE | INACTIVE (news: PUBLISHED | UNPUBLISHED). */
        String status
) {
}

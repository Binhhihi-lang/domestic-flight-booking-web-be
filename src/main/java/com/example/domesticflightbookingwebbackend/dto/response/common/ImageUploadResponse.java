package com.example.domesticflightbookingwebbackend.dto.response.common;

/** Kết quả tải ảnh lên từ khu vực quản trị ({@code POST /api/admin/uploads/image}). */
public record ImageUploadResponse(
        String url,
        String fileName,
        String contentType,
        long size
) {
}

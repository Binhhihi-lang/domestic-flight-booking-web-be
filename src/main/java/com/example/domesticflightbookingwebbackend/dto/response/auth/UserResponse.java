package com.example.domesticflightbookingwebbackend.dto.response.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Thông tin user trả về client (mục 2.1 API v0.2).
 *
 * <p>Khớp hợp đồng: dùng {@code status} dạng chuỗi ACTIVE/INACTIVE và
 * {@code roles} dạng mảng — dù DB v0.4 lưu {@code is_active} boolean và
 * {@code role_id} đơn. Việc chuyển đổi nằm ở mapper.
 *
 * <p>KHÔNG bao giờ chứa {@code password_hash}.
 */
public record UserResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        String avatarUrl,
        String status,
        List<String> roles,
        LocalDateTime createdAt
) {
}

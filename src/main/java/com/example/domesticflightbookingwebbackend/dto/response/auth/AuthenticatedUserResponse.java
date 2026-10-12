package com.example.domesticflightbookingwebbackend.dto.response.auth;

import java.util.List;

/** User kèm danh sách permission — chỉ có trong response login (mục 2.1). */
public record AuthenticatedUserResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        String avatarUrl,
        String status,
        List<String> roles,
        List<String> permissions
) {
}

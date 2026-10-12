package com.example.domesticflightbookingwebbackend.dto.request.user;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Payload PUT /users/{id} (USR-04 gộp sửa info + đổi role + toggle trạng thái).
 * {@code password} KHÔNG nằm ở đây — ADMIN đặt lại qua /users/{id}/reset-password.
 */
public record UpdateUserRequest(

        @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
        String fullName,

        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
        String phone,

        /** ACTIVE | INACTIVE */
        String status,

        @NotEmpty(message = "Phải chọn ít nhất một role")
        List<String> roles
) {
}

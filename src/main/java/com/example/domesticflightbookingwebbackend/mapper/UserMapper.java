package com.example.domesticflightbookingwebbackend.mapper;

import com.example.domesticflightbookingwebbackend.dto.response.auth.AuthenticatedUserResponse;
import com.example.domesticflightbookingwebbackend.dto.response.auth.UserResponse;
import com.example.domesticflightbookingwebbackend.entity.Permission;
import com.example.domesticflightbookingwebbackend.entity.Role;
import com.example.domesticflightbookingwebbackend.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Chuyển {@link User} (DB v0.4: {@code is_active} + {@code role_id}) sang DTO
 * theo hợp đồng API v0.2 ({@code status} chuỗi ACTIVE/INACTIVE + {@code roles} mảng).
 */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getAvatarUrl(),
                toStatus(user),
                toRoleNames(user),
                user.getCreatedAt()
        );
    }

    public AuthenticatedUserResponse toAuthenticatedResponse(User user, List<String> permissions) {
        return new AuthenticatedUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getAvatarUrl(),
                toStatus(user),
                toRoleNames(user),
                permissions
        );
    }

    private String toStatus(User user) {
        return Boolean.TRUE.equals(user.getIsActive()) ? "ACTIVE" : "INACTIVE";
    }

    private List<String> toRoleNames(User user) {
        Role role = user.getRole();
        return role == null ? List.of() : List.of(role.getName());
    }

    /** Danh sách permission_key của role — dùng cho response login. */
    public List<String> extractPermissionKeys(User user) {
        Role role = user.getRole();
        if (role == null || role.getPermissions() == null) {
            return List.of();
        }
        return role.getPermissions().stream()
                .map(Permission::getPermissionKey)
                .sorted()
                .toList();
    }
}

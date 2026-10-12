package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.request.profile.UpdateProfileRequest;
import com.example.domesticflightbookingwebbackend.dto.response.auth.UserResponse;
import com.example.domesticflightbookingwebbackend.entity.User;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.mapper.UserMapper;
import com.example.domesticflightbookingwebbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profile của chính người đang đăng nhập (mục 2.2 API v0.2).
 * Luôn lấy user từ token — không nhận userId từ client (chặn truy cập chéo).
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return userMapper.toResponse(findActive(userId));
    }

    /** Chỉ sửa được full_name / phone / avatar_url; field khác không nằm trong DTO. */
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findActive(userId);

        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone().isBlank() ? null : request.phone().trim());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().isBlank() ? null : request.avatarUrl().trim());
        }

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateAvatar(Long userId, String avatarUrl) {
        User user = findActive(userId);
        user.setAvatarUrl(avatarUrl);
        return userMapper.toResponse(userRepository.save(user));
    }

    private User findActive(Long userId) {
        return userRepository.findById(userId)
                .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Người dùng không tồn tại"));
    }
}

package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.dto.request.profile.UpdateProfileRequest;
import com.example.domesticflightbookingwebbackend.dto.response.auth.UserResponse;
import com.example.domesticflightbookingwebbackend.security.SecurityUtils;
import com.example.domesticflightbookingwebbackend.service.CloudinaryService;
import com.example.domesticflightbookingwebbackend.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** Profile của chính người đang đăng nhập (mục 2.2 API v0.2). */
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final CloudinaryService cloudinaryService;

    @GetMapping
    @PreAuthorize("hasAuthority('PROFILE_READ_SELF')")
    public ApiResponse<UserResponse> getProfile() {
        return ApiResponse.success(profileService.getProfile(SecurityUtils.getCurrentUserId()));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('PROFILE_UPDATE_SELF')")
    public ApiResponse<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(
                "Cập nhật hồ sơ thành công",
                profileService.updateProfile(SecurityUtils.getCurrentUserId(), request));
    }

    /** Upload avatar lên Cloudinary, trả URL công khai. */
    @PostMapping("/avatar")
    @PreAuthorize("hasAuthority('PROFILE_UPDATE_SELF')")
    public ApiResponse<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String url = cloudinaryService.uploadAvatar(file);
        profileService.updateAvatar(SecurityUtils.getCurrentUserId(), url);
        return ApiResponse.success("Cập nhật ảnh đại diện thành công", Map.of("avatar_url", url));
    }
}

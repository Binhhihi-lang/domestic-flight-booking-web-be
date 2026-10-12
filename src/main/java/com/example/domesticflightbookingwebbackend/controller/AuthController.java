package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.dto.request.auth.ChangePasswordRequest;
import com.example.domesticflightbookingwebbackend.dto.request.auth.LoginRequest;
import com.example.domesticflightbookingwebbackend.dto.request.auth.PasswordResetConfirmRequest;
import com.example.domesticflightbookingwebbackend.dto.request.auth.PasswordResetRequest;
import com.example.domesticflightbookingwebbackend.dto.request.auth.RegisterRequest;
import com.example.domesticflightbookingwebbackend.dto.response.auth.LoginResponse;
import com.example.domesticflightbookingwebbackend.dto.response.auth.PasswordResetResponse;
import com.example.domesticflightbookingwebbackend.dto.response.auth.UserResponse;
import com.example.domesticflightbookingwebbackend.security.SecurityUtils;
import com.example.domesticflightbookingwebbackend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authentication & Account (mục 2.1 — API Design v0.2). */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký thành công", authService.register(request)));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/logout")
    @PreAuthorize("hasAuthority('LOGOUT')")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(extractBearerToken(authorization));
        return ApiResponse.success(null);
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@RequestBody RefreshTokenBody body) {
        return ApiResponse.success(authService.refresh(body.refreshToken()));
    }

    /**
     * Luôn trả 200 message trung tính (chống dò tài khoản). MVP trả token thẳng
     * trong {@code data} để test được luồng đặt lại mật khẩu.
     */
    @PostMapping("/password-reset/request")
    public ApiResponse<PasswordResetResponse> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        String token = authService.requestPasswordReset(request);
        return ApiResponse.success("Nếu email tồn tại, hướng dẫn đã được gửi",
                new PasswordResetResponse(token));
    }

    @PostMapping("/password-reset/confirm")
    public ApiResponse<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request);
        return ApiResponse.success("Đặt lại mật khẩu thành công", null);
    }

    @PostMapping("/change-password")
    @PreAuthorize("hasAuthority('PASSWORD_CHANGE_SELF')")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(SecurityUtils.getCurrentUserId(), request);
        return ApiResponse.success("Đổi mật khẩu thành công", null);
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7);
    }

    /** Body của POST /auth/refresh. */
    public record RefreshTokenBody(String refreshToken) {
    }
}

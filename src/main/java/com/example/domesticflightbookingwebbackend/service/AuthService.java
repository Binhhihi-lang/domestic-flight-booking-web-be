package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.request.auth.ChangePasswordRequest;
import com.example.domesticflightbookingwebbackend.dto.request.auth.LoginRequest;
import com.example.domesticflightbookingwebbackend.dto.request.auth.PasswordResetConfirmRequest;
import com.example.domesticflightbookingwebbackend.dto.request.auth.PasswordResetRequest;
import com.example.domesticflightbookingwebbackend.dto.request.auth.RegisterRequest;
import com.example.domesticflightbookingwebbackend.dto.response.auth.AuthenticatedUserResponse;
import com.example.domesticflightbookingwebbackend.dto.response.auth.LoginResponse;
import com.example.domesticflightbookingwebbackend.dto.response.auth.UserResponse;
import com.example.domesticflightbookingwebbackend.entity.Role;
import com.example.domesticflightbookingwebbackend.entity.User;
import com.example.domesticflightbookingwebbackend.entity.redis.InvalidatedToken;
import com.example.domesticflightbookingwebbackend.entity.redis.PasswordResetToken;
import com.example.domesticflightbookingwebbackend.entity.redis.RefreshToken;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.DuplicateResourceException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.mapper.UserMapper;
import com.example.domesticflightbookingwebbackend.repository.RoleRepository;
import com.example.domesticflightbookingwebbackend.repository.UserRepository;
import com.example.domesticflightbookingwebbackend.repository.redis.InvalidatedTokenRepository;
import com.example.domesticflightbookingwebbackend.repository.redis.PasswordResetTokenRepository;
import com.example.domesticflightbookingwebbackend.repository.redis.RefreshTokenRepository;
import com.example.domesticflightbookingwebbackend.security.JwtService;
import com.example.domesticflightbookingwebbackend.security.UserDetailsServiceImpl;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Nghiệp vụ Authentication & Account (mục 2.1 — API Design v0.2). */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** TTL token đặt lại mật khẩu (giây) — quyết định open item #2: 15 phút. */
    private static final long PASSWORD_RESET_TTL_SECONDS = 15 * 60;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final UserDetailsServiceImpl userDetailsService;
    private final InvalidatedTokenRepository invalidatedTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    // ================== REGISTER ==================

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailAndIsDeletedFalse(email)) {
            throw new DuplicateResourceException("Email đã được sử dụng");
        }

        Role customerRole = roleRepository.findByName("CUSTOMER")
                .orElseThrow(() -> new BusinessException("Role CUSTOMER chưa được seed"));

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone());
        user.setRole(customerRole);
        user.setIsActive(true);
        user.setIsDeleted(false);
        userRepository.save(user);

        return userMapper.toResponse(user);
    }

    // ================== LOGIN ==================

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailAndIsDeletedFalse(email).orElse(null);

        // Message 1007 KHÔNG phân biệt sai email hay sai mật khẩu (chống dò tài khoản)
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new AppException(ErrorCode.ACCOUNT_INACTIVE);
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        saveRefreshToken(user.getId(), refreshToken);

        AuthenticatedUserResponse userResponse = userMapper.toAuthenticatedResponse(
                user, userMapper.extractPermissionKeys(user));

        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getAccessDurationSeconds(),
                userResponse
        );
    }

    // ================== LOGOUT ==================

    /**
     * Đưa access token vào blacklist (TTL = thời gian còn lại) và thu hồi
     * refresh token. Nhờ vậy token cũ bị từ chối ngay ở request kế tiếp.
     */
    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        try {
            SignedJWT jwt = jwtService.parse(accessToken);
            String jwtId = jwt.getJWTClaimsSet().getJWTID();
            long remaining = jwtService.remainingSeconds(jwt);
            if (jwtId != null && remaining > 0) {
                invalidatedTokenRepository.save(InvalidatedToken.builder()
                        .id(jwtId)
                        .ttl(remaining)
                        .build());
            }
            String userId = jwt.getJWTClaimsSet().getSubject();
            if (userId != null) {
                revokeAllRefreshTokens(Long.valueOf(userId));
            }
        } catch (Exception e) {
            // Token hỏng/không parse được: logout vẫn coi như thành công (client xóa token)
            log.warn("Logout với token không hợp lệ: {}", e.getMessage());
        }
    }

    // ================== REFRESH ==================

    /**
     * Xoay vòng refresh token: xóa token cũ, cấp cặp mới, GIỮ NGUYÊN hạn tuyệt
     * đối của phiên (không kéo dài vô hạn).
     */
    @Transactional(readOnly = true)
    public LoginResponse refresh(String refreshToken) {
        SignedJWT jwt;
        try {
            jwt = jwtService.parse(refreshToken);
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String jwtId;
        String userId;
        Date expiry;
        try {
            jwtId = jwt.getJWTClaimsSet().getJWTID();
            userId = jwt.getJWTClaimsSet().getSubject();
            expiry = jwt.getJWTClaimsSet().getExpirationTime();
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        // Refresh token phải còn trong whitelist Redis (chưa bị thu hồi)
        if (refreshTokenRepository.findById(jwtId).isEmpty()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        if (expiry == null || expiry.before(new Date())) {
            refreshTokenRepository.deleteById(jwtId);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        User user = userRepository.findById(Long.valueOf(userId)).orElse(null);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())
                || !Boolean.TRUE.equals(user.getIsActive())) {
            throw new AppException(ErrorCode.ACCOUNT_INACTIVE);
        }

        refreshTokenRepository.deleteById(jwtId);

        Instant absoluteExpiry = expiry.toInstant();
        String newAccess = jwtService.generateAccessToken(user);
        String newRefresh = jwtService.generateRefreshTokenWithExpiry(user, absoluteExpiry);
        saveRefreshToken(user.getId(), newRefresh);

        AuthenticatedUserResponse userResponse = userMapper.toAuthenticatedResponse(
                user, userMapper.extractPermissionKeys(user));

        return new LoginResponse(newAccess, newRefresh, "Bearer",
                jwtService.getAccessDurationSeconds(), userResponse);
    }

    // ================== PASSWORD RESET ==================

    /**
     * Luôn trả token/message trung tính dù email có tồn tại hay không (chống dò
     * tài khoản). MVP chưa có mail server nên trả token trong response.
     */
    @Transactional(readOnly = true)
    public String requestPasswordReset(PasswordResetRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailAndIsDeletedFalse(email).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
            return null;
        }

        // Thu hồi token reset cũ trước khi phát token mới
        passwordResetTokenRepository.deleteAll(
                passwordResetTokenRepository.findByUserId(String.valueOf(user.getId())));

        String token = UUID.randomUUID().toString();
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .id(token)
                .userId(String.valueOf(user.getId()))
                .ttl(PASSWORD_RESET_TTL_SECONDS)
                .build());
        return token;
    }


    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        PasswordResetToken stored = passwordResetTokenRepository.findById(request.token())
                .orElseThrow(() -> new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn"));

        User user = userRepository.findById(Long.valueOf(stored.getUserId()))
                .orElseThrow(() -> new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn"));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Token dùng một lần
        passwordResetTokenRepository.deleteById(request.token());
        // Đổi mật khẩu → thu hồi mọi phiên đăng nhập cũ
        revokeAllRefreshTokens(user.getId());
        userDetailsService.evictAuthorities(user.getId());
    }

    // ================== CHANGE PASSWORD ==================

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        revokeAllRefreshTokens(user.getId());
        userDetailsService.evictAuthorities(user.getId());
    }

    // ================== Helpers ==================

    private void saveRefreshToken(Long userId, String refreshToken) {
        try {
            SignedJWT jwt = jwtService.parse(refreshToken);
            String jwtId = jwt.getJWTClaimsSet().getJWTID();
            long ttl = jwtService.remainingSeconds(jwt);
            refreshTokenRepository.save(new RefreshToken(jwtId, String.valueOf(userId), ttl));
        } catch (Exception e) {
            throw new IllegalStateException("Không lưu được refresh token", e);
        }
    }

    private void revokeAllRefreshTokens(Long userId) {
        List<RefreshToken> tokens = refreshTokenRepository.findByUserId(String.valueOf(userId));
        refreshTokenRepository.deleteAll(tokens);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}

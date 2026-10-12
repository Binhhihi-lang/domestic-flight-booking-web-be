package com.example.domesticflightbookingwebbackend.security;

import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

/**
 * Tiện ích lấy user hiện tại TỪ TOKEN.
 *
 * <p>Endpoint SELF (profile, my-vouchers, booking của tôi) luôn dùng lớp này
 * thay vì nhận {@code user_id} từ client — chặn truy cập chéo tài khoản
 * (mục 1.5 API Design v0.2, quyết định #1 kế hoạch BE).
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** User id của người đang đăng nhập; ném 1002 nếu chưa xác thực. */
    public static Long getCurrentUserId() {
        return currentJwt()
                .map(jwt -> Long.valueOf(jwt.getSubject()))
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }

    public static Optional<Jwt> currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(jwt);
        }
        return Optional.empty();
    }

    /** {@code true} nếu người đang đăng nhập có authority (permission_key) này. */
    public static boolean hasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }
}

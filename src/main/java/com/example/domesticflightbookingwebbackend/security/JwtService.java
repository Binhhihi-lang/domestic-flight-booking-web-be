package com.example.domesticflightbookingwebbackend.security;

import com.example.domesticflightbookingwebbackend.entity.Permission;
import com.example.domesticflightbookingwebbackend.entity.Role;
import com.example.domesticflightbookingwebbackend.entity.User;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Tạo và đọc JWT (thuật toán đối xứng HS256 — quyết định ở kế hoạch cụm 3).
 *
 * <p>Access token mang: {@code sub} = user id, {@code email}, {@code permissions}
 * (danh sách permission_key) và {@code roles} — đúng mục 1.5 API Design v0.2.
 * Việc kiểm tra quyền ở backend KHÔNG tin vào claim này mà đọc lại từ
 * {@code CachedAuthorities}/DB, nên khóa role có hiệu lực ngay.
 */
@Slf4j
@Service
public class JwtService {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_PERMISSIONS = "permissions";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final String signerKey;
    private final long accessDurationSeconds;
    private final long refreshableDurationSeconds;

    public JwtService(
            @Value("${jwt.signer-key}") String signerKey,
            @Value("${jwt.access-duration}") long accessDurationSeconds,
            @Value("${jwt.refreshable-duration}") long refreshableDurationSeconds) {
        this.signerKey = signerKey;
        this.accessDurationSeconds = accessDurationSeconds;
        this.refreshableDurationSeconds = refreshableDurationSeconds;
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return buildToken(user, now, now.plus(accessDurationSeconds, ChronoUnit.SECONDS), TYPE_ACCESS);
    }

    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        return buildToken(user, now, now.plus(refreshableDurationSeconds, ChronoUnit.SECONDS), TYPE_REFRESH);
    }

    /** Tạo refresh token mới nhưng giữ nguyên hạn tuyệt đối của phiên (khi xoay vòng). */
    public String generateRefreshTokenWithExpiry(User user, Instant expiry) {
        return buildToken(user, Instant.now(), expiry, TYPE_REFRESH);
    }

    private String buildToken(User user, Instant issueTime, Instant expiry, String type) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);

        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(String.valueOf(user.getId()))
                .issuer("domestic-flight-booking")
                .issueTime(Date.from(issueTime))
                .expirationTime(Date.from(expiry))
                .jwtID(UUID.randomUUID().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_ROLES, List.of("ROLE_" + user.getRole().getName()))
                .claim(CLAIM_PERMISSIONS, extractPermissionKeys(user));

        Payload payload = new Payload(claims.build().toJSONObject());
        JWSObject jws = new JWSObject(header, payload);
        try {
            jws.sign(new MACSigner(signerKey.getBytes()));
            return jws.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Không ký được JWT", e);
        }
    }

    private List<String> extractPermissionKeys(User user) {
        Role role = user.getRole();
        if (role == null || role.getPermissions() == null) {
            return List.of();
        }
        return role.getPermissions().stream()
                .map(Permission::getPermissionKey)
                .sorted()
                .toList();
    }

    /** Parse token chưa verify — chỉ dùng để đọc {@code jti}/hạn khi logout. */
    public SignedJWT parse(String token) {
        try {
            return SignedJWT.parse(token);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Token không hợp lệ", e);
        }
    }

    /** Thời gian còn lại của token (giây) — dùng làm TTL khi đưa vào blacklist. */
    public long remainingSeconds(SignedJWT jwt) {
        try {
            Date expiry = jwt.getJWTClaimsSet().getExpirationTime();
            long remaining = (expiry.getTime() - System.currentTimeMillis()) / 1000;
            return Math.max(remaining, 0);
        } catch (ParseException e) {
            return 0;
        }
    }

    public long getAccessDurationSeconds() {
        return accessDurationSeconds;
    }

    public long getRefreshableDurationSeconds() {
        return refreshableDurationSeconds;
    }
}

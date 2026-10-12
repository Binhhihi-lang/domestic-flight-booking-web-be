package com.example.domesticflightbookingwebbackend.security;

import com.example.domesticflightbookingwebbackend.entity.Permission;
import com.example.domesticflightbookingwebbackend.entity.Role;
import com.example.domesticflightbookingwebbackend.entity.User;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SIGNER_KEY = "test-secret-key-that-is-long-enough-for-hs256-1234567890";

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SIGNER_KEY, 3600, 1209600);
        user = buildUser();
    }

    @Test
    void accessToken_mangDuClaimTheoHopDong() throws Exception {
        String token = jwtService.generateAccessToken(user);

        JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getStringClaim("email")).isEqualTo("binh@example.com");
        assertThat(claims.getStringClaim("type")).isEqualTo("access");
        assertThat(claims.getJWTID()).isNotBlank();
        assertThat(claims.getStringListClaim("roles")).containsExactly("ROLE_CUSTOMER");
        assertThat(claims.getStringListClaim("permissions"))
                .containsExactly("BOOKING_CREATE", "PROFILE_READ_SELF");   // đã sort
    }

    @Test
    void refreshToken_coTypeRefreshVaHanDaiHon() throws Exception {
        String token = jwtService.generateRefreshToken(user);

        JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();

        assertThat(claims.getStringClaim("type")).isEqualTo("refresh");
        long accessTtl = jwtService.getAccessDurationSeconds();
        long refreshTtl = jwtService.getRefreshableDurationSeconds();
        assertThat(refreshTtl).isGreaterThan(accessTtl);
    }

    @Test
    void remainingSeconds_giamDanVaKhongAm() throws Exception {
        String token = jwtService.generateAccessToken(user);
        SignedJWT parsed = jwtService.parse(token);

        long remaining = jwtService.remainingSeconds(parsed);

        assertThat(remaining).isBetween(0L, 3600L);
    }

    @Test
    void generateRefreshTokenWithExpiry_giuNguyenHanTuyetDoi() throws Exception {
        String first = jwtService.generateRefreshToken(user);
        SignedJWT firstParsed = jwtService.parse(first);
        var originalExpiry = firstParsed.getJWTClaimsSet().getExpirationTime();

        String rotated = jwtService.generateRefreshTokenWithExpiry(user, originalExpiry.toInstant());

        JWTClaimsSet rotatedClaims = SignedJWT.parse(rotated).getJWTClaimsSet();
        assertThat(rotatedClaims.getExpirationTime()).isEqualTo(originalExpiry);
    }

    @Test
    void moiTokenCoJtiKhacNhau() throws Exception {
        String t1 = jwtService.generateAccessToken(user);
        String t2 = jwtService.generateAccessToken(user);

        assertThat(SignedJWT.parse(t1).getJWTClaimsSet().getJWTID())
                .isNotEqualTo(SignedJWT.parse(t2).getJWTClaimsSet().getJWTID());
    }

    private User buildUser() {
        Permission readProfile = new Permission();
        readProfile.setPermissionKey("PROFILE_READ_SELF");
        Permission createBooking = new Permission();
        createBooking.setPermissionKey("BOOKING_CREATE");

        Role role = new Role();
        role.setName("CUSTOMER");
        Set<Permission> permissions = new HashSet<>();
        permissions.add(readProfile);
        permissions.add(createBooking);
        role.setPermissions(permissions);

        User u = new User();
        u.setId(42L);
        u.setEmail("binh@example.com");
        u.setRole(role);
        return u;
    }
}

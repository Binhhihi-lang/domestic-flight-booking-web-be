package com.example.domesticflightbookingwebbackend.dto.response.auth;

/** `data` của POST /auth/login (mục 2.1 API v0.2). */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        AuthenticatedUserResponse user
) {
}

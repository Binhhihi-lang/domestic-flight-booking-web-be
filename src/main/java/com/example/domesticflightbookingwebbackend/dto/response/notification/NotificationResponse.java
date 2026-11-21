package com.example.domesticflightbookingwebbackend.dto.response.notification;

import java.time.LocalDateTime;

/**
 * Một thông báo in-app trả về cho client ({@code GET /api/notifications}).
 */
public record NotificationResponse(
        Long id,
        String type,
        String title,
        String body,
        String linkUrl,
        Boolean isRead,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
}

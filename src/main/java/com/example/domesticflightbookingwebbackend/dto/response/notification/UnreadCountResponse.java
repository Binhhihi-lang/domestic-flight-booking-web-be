package com.example.domesticflightbookingwebbackend.dto.response.notification;

/**
 * Số lượng thông báo chưa đọc của người dùng hiện tại ({@code GET /api/notifications/unread-count}).
 */
public record UnreadCountResponse(
        long unreadCount
) {
}

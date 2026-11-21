package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.response.notification.NotificationResponse;
import com.example.domesticflightbookingwebbackend.dto.response.notification.UnreadCountResponse;
import com.example.domesticflightbookingwebbackend.security.SecurityUtils;
import com.example.domesticflightbookingwebbackend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API Thông báo In-app cho người dùng đang đăng nhập (Polling 60 giây — Phase 3 Cụm B5.1).
 *
 * <p>Mỗi người dùng (CUSTOMER, STAFF, ADMIN) chỉ đọc và đánh dấu thông báo của chính mình
 * thông qua quyền {@code NOTIFICATION_READ_SELF}.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_READ_SELF')")
    public ApiResponse<PageData<NotificationResponse>> listSelf(
            @RequestParam(name = "unread", required = false) Boolean unread,
            ListQuery query) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(notificationService.listSelf(currentUserId, unread, query));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ_SELF')")
    public ApiResponse<UnreadCountResponse> unreadCount() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(new UnreadCountResponse(notificationService.countUnread(currentUserId)));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ_SELF')")
    public ApiResponse<Void> markAsRead(@PathVariable Long id) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        notificationService.markAsRead(currentUserId, id);
        return ApiResponse.success("Đã đánh dấu đã đọc", null);
    }

    @PatchMapping("/read-all")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ_SELF')")
    public ApiResponse<Void> markAllAsRead() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        notificationService.markAllAsRead(currentUserId);
        return ApiResponse.success("Đã đánh dấu tất cả thông báo là đã đọc", null);
    }
}

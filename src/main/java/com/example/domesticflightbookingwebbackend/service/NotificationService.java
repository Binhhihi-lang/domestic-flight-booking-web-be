package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.dto.response.notification.NotificationResponse;
import com.example.domesticflightbookingwebbackend.entity.Booking;
import com.example.domesticflightbookingwebbackend.entity.BookingStatus;
import com.example.domesticflightbookingwebbackend.entity.Notification;
import com.example.domesticflightbookingwebbackend.entity.NotificationType;
import com.example.domesticflightbookingwebbackend.entity.User;
import com.example.domesticflightbookingwebbackend.entity.UserVoucher;
import com.example.domesticflightbookingwebbackend.entity.UserVoucherStatus;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.repository.BookingRepository;
import com.example.domesticflightbookingwebbackend.repository.NotificationRepository;
import com.example.domesticflightbookingwebbackend.repository.UserRepository;
import com.example.domesticflightbookingwebbackend.repository.UserVoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Dịch vụ Thông báo In-app (Polling 60 giây — Phase 3 Cụm B5.1).
 *
 * <p>Quy tắc nghiệp vụ (chốt 18/11/2026 — {@code fe/docs/design/00-nghiep-vu-thong-bao.md}):
 * <ul>
 *   <li><b>BR-NOTI-01:</b> Mỗi user (CUSTOMER / STAFF / ADMIN) chỉ đọc và đánh dấu đã đọc thông báo
 *       của chính mình; không lộ API tạo thông báo ra ngoài và không cho ADMIN xem hộ người khác.</li>
 *   <li><b>BR-NOTI-03:</b> Dùng {@code dedupe_key} (kèm unique index {@code uq_notifications_dedupe})
 *       để chống phát thông báo trùng lặp khi callback thanh toán đến 2 lần hoặc job chạy lặp.</li>
 *   <li><b>BR-NOTI-05:</b> Không soft-delete; job định kỳ xóa cứng các thông báo quá 90 ngày.</li>
 *   <li><b>BR-NOTI-06:</b> {@code VOUCHER_EXPIRING} chỉ phát đúng 1 lần cho mỗi {@code user_voucher}
 *       khi còn ≤ 3 ngày trước hạn dùng.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int RETENTION_DAYS = 90;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final UserVoucherRepository userVoucherRepository;

    /**
     * Danh sách thông báo của chính người dùng đang đăng nhập ({@code GET /api/notifications}).
     * Hỗ trợ cả {@code page=0} (0-based như đặc tả thông báo) lẫn {@code page>=1} (1-based chuẩn chung).
     */
    @Transactional(readOnly = true)
    public PageData<NotificationResponse> listSelf(Long userId, Boolean unreadOnly, ListQuery query) {
        int rawPage = (query == null || query.getPage() == null) ? 1 : query.getPage();
        int zeroBasedPage = rawPage <= 0 ? 0 : rawPage - 1;
        int size = (query == null) ? 20 : query.getSizeOrDefault();
        Pageable pageable = PageRequest.of(zeroBasedPage, size);

        Page<Notification> page;
        if (Boolean.TRUE.equals(unreadOnly)) {
            page = notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false, pageable);
        } else {
            page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return PageMapper.toPageData(page, this::toResponse);
    }

    /**
     * Đếm số thông báo chưa đọc của chính người dùng ({@code GET /api/notifications/unread-count}).
     * FE gọi endpoint này mỗi 60 giây khi tab đang hiển thị.
     */
    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    /**
     * Đánh dấu 1 thông báo đã đọc ({@code PATCH /api/notifications/{id}/read}).
     * Bắt buộc kiểm tra chủ sở hữu {@code user_id} (chống IDOR).
     */
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Thông báo không tồn tại"));
        if (!notification.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền thao tác trên thông báo này");
        }
        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
    }

    /**
     * Đánh dấu toàn bộ thông báo của chính người dùng đã đọc ({@code PATCH /api/notifications/read-all}).
     */
    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsReadByUserId(userId, LocalDateTime.now());
    }

    /**
     * Hàm nội bộ phát thông báo cho một người dùng cụ thể, chống trùng bằng {@code dedupeKey}.
     */
    @Transactional
    public void notify(
            Long userId,
            NotificationType type,
            String title,
            String body,
            String linkUrl,
            String dedupeKey) {
        if (userId == null) {
            return;
        }
        if (dedupeKey != null && !dedupeKey.isBlank()
                && notificationRepository.existsByUserIdAndDedupeKey(userId, dedupeKey)) {
            log.debug("Bỏ qua thông báo trùng dedupeKey={} cho userId={}", dedupeKey, userId);
            return;
        }

        User user = userRepository.findById(userId)
                .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                .orElse(null);
        if (user == null) {
            return;
        }

        Notification n = new Notification();
        n.setUser(user);
        n.setType(type);
        n.setTitle(truncate(title, 200));
        n.setBody(truncate(body, 500));
        n.setLinkUrl(truncate(linkUrl, 300));
        n.setIsRead(false);
        n.setDedupeKey(dedupeKey == null || dedupeKey.isBlank() ? null : truncate(dedupeKey, 120));
        n.setCreatedAt(LocalDateTime.now());

        try {
            notificationRepository.save(n);
        } catch (DataIntegrityViolationException e) {
            log.debug("Chặn trùng thông báo ở tầng DB cho userId={}, dedupeKey={}", userId, dedupeKey);
        }
    }

    /**
     * Phát thông báo vận hành cho toàn bộ tài khoản STAFF và ADMIN đang hoạt động.
     */
    @Transactional
    public void notifyStaffAndAdmins(
            NotificationType type,
            String title,
            String body,
            String linkUrl,
            String dedupeKey) {
        List<User> operators = userRepository.findByRoleNameInAndIsActiveTrueAndIsDeletedFalse(
                List.of("STAFF", "ADMIN"));
        for (User op : operators) {
            notify(op.getId(), type, title, body, linkUrl, dedupeKey);
        }
    }

    /**
     * Job định kỳ xóa cứng các bản ghi thông báo quá 90 ngày (BR-NOTI-05).
     */
    @Scheduled(cron = "${app.notification.purge-cron:0 30 3 * * *}")
    @Transactional
    public int purgeOlderThan90Days() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int deleted = notificationRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Đã xóa cứng {} thông báo cũ quá {} ngày", deleted, RETENTION_DAYS);
        }
        return deleted;
    }

    /**
     * Job định kỳ quét các sự kiện sắp hết hạn để phát thông báo:
     * 1. {@code BOOKING_PAYMENT_EXPIRING}: Đơn {@code PENDING_PAYMENT} còn ≤ 5 phút trước {@code payment_expires_at}.
     * 2. {@code VOUCHER_EXPIRING}: Voucher trong ví khách đang {@code AVAILABLE} còn ≤ 3 ngày trước {@code expires_at} (BR-NOTI-06).
     */
    @Scheduled(fixedDelayString = "${app.notification.expiring-scan-ms:60000}")
    @Transactional
    public void scanAndNotifyExpiringItems() {
        LocalDateTime now = LocalDateTime.now();

        // 1. BOOKING_PAYMENT_EXPIRING (còn <= 5 phút)
        List<Booking> expiringBookings = bookingRepository.findByStatusAndPaymentExpiresAtBetween(
                BookingStatus.PENDING_PAYMENT, now, now.plusMinutes(5));
        for (Booking b : expiringBookings) {
            String dedupe = "booking:" + b.getId() + ":expiring";
            String title = "Đơn đặt vé " + b.getBookingCode() + " sắp hết hạn thanh toán";
            String body = "Thời hạn giữ chỗ 15 phút của đơn " + b.getBookingCode() + " sẽ kết thúc trong ít phút nữa.";
            if (b.getUser() != null) {
                notify(b.getUser().getId(), NotificationType.BOOKING_PAYMENT_EXPIRING,
                        title, body, "/account/bookings/" + b.getId(), dedupe);
            }
            notifyStaffAndAdmins(NotificationType.BOOKING_PAYMENT_EXPIRING,
                    title, body, "/admin/bookings/" + b.getId(), dedupe);
        }

        // 2. VOUCHER_EXPIRING (còn <= 3 ngày — BR-NOTI-06: chỉ gửi đúng 1 lần mỗi user_voucher)
        List<UserVoucher> expiringVouchers = userVoucherRepository.findByStatusAndExpiresAtBetween(
                UserVoucherStatus.AVAILABLE, now, now.plusDays(3));
        for (UserVoucher uv : expiringVouchers) {
            if (uv.getUser() == null || uv.getVoucher() == null) {
                continue;
            }
            String dedupe = "user_voucher:" + uv.getId() + ":expiring";
            notify(
                    uv.getUser().getId(),
                    NotificationType.VOUCHER_EXPIRING,
                    "Voucher " + uv.getVoucher().getVoucherName() + " sắp hết hạn trong 3 ngày",
                    "Hãy sử dụng voucher trước ngày " + uv.getVoucher().getEndDate() + " để nhận ưu đãi.",
                    "/account/vouchers",
                    dedupe);
        }
    }

    public NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType().name(),
                n.getTitle(),
                n.getBody(),
                n.getLinkUrl(),
                n.getIsRead(),
                n.getReadAt(),
                n.getCreatedAt()
        );
    }

    private String truncate(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}

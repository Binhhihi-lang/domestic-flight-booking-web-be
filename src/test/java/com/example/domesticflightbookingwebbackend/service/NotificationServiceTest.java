package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.response.notification.NotificationResponse;
import com.example.domesticflightbookingwebbackend.entity.Notification;
import com.example.domesticflightbookingwebbackend.entity.NotificationType;
import com.example.domesticflightbookingwebbackend.entity.User;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.repository.BookingRepository;
import com.example.domesticflightbookingwebbackend.repository.NotificationRepository;
import com.example.domesticflightbookingwebbackend.repository.UserRepository;
import com.example.domesticflightbookingwebbackend.repository.UserVoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private NotificationRepository notificationRepository;
    private UserRepository userRepository;
    private BookingRepository bookingRepository;
    private UserVoucherRepository userVoucherRepository;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        userRepository = mock(UserRepository.class);
        bookingRepository = mock(BookingRepository.class);
        userVoucherRepository = mock(UserVoucherRepository.class);

        service = new NotificationService(
                notificationRepository,
                userRepository,
                bookingRepository,
                userVoucherRepository);
    }

    @Test
    void notify_chuaCoDedupeKey_luuThongBaoMoi() {
        User u = user(1L);
        when(notificationRepository.existsByUserIdAndDedupeKey(1L, "booking:1049:confirmed")).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));

        service.notify(
                1L,
                NotificationType.BOOKING_CONFIRMED,
                "Đơn DH-1049 đã thanh toán",
                "Chi tiết",
                "/account/bookings/1049",
                "booking:1049:confirmed");

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void notify_trungDedupeKey_boQuaKhongPhatTrung() {
        when(notificationRepository.existsByUserIdAndDedupeKey(1L, "booking:1049:confirmed")).thenReturn(true);

        service.notify(
                1L,
                NotificationType.BOOKING_CONFIRMED,
                "Đơn DH-1049 đã thanh toán",
                "Chi tiết",
                "/account/bookings/1049",
                "booking:1049:confirmed");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAsRead_dungChuSoHuu_danhDauDaDoc() {
        User u = user(1L);
        Notification n = new Notification();
        n.setId(50L);
        n.setUser(u);
        n.setIsRead(false);

        when(notificationRepository.findById(50L)).thenReturn(Optional.of(n));

        service.markAsRead(1L, 50L);

        assertThat(n.getIsRead()).isTrue();
        assertThat(n.getReadAt()).isNotNull();
        verify(notificationRepository).save(n);
    }

    @Test
    void markAsRead_khacChuSoHuu_nem1003ChongIdor() {
        User u = user(2L);
        Notification n = new Notification();
        n.setId(50L);
        n.setUser(u);

        when(notificationRepository.findById(50L)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.markAsRead(1L, 50L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void listSelf_hoTroPage0VaLocUnread() {
        Notification n = new Notification();
        n.setId(10L);
        n.setType(NotificationType.ETICKET_ISSUED);
        n.setTitle("Vé điện tử đã phát hành");
        n.setIsRead(false);
        n.setCreatedAt(LocalDateTime.now());

        when(notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(eq(1L), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(n)));

        ListQuery query = new ListQuery();
        query.setPage(0);
        query.setSize(20);
        PageData<NotificationResponse> res = service.listSelf(1L, true, query);

        assertThat(res.items()).hasSize(1);
        assertThat(res.items().get(0).type()).isEqualTo("ETICKET_ISSUED");
    }

    @Test
    void purgeOlderThan90Days_goiXoaCung() {
        when(notificationRepository.deleteOlderThan(any(LocalDateTime.class))).thenReturn(12);

        int deleted = service.purgeOlderThan90Days();

        assertThat(deleted).isEqualTo(12);
    }

    private User user(Long id) {
        User u = new User();
        u.setId(id);
        u.setIsDeleted(false);
        return u;
    }
}

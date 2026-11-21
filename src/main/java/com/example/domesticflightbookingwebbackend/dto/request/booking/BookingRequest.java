package com.example.domesticflightbookingwebbackend.dto.request.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Payload POST /api/bookings (Phase 3 — B3.3).
 *
 * <p>Một đơn đặt vé có 1 chặng ({@code ONE_WAY}) hoặc 2 chặng ({@code ROUND_TRIP}),
 * kèm danh sách hành khách (tối đa 9 khách, số em bé ≤ số người lớn).
 * Có thể gửi kèm {@code user_voucher_id} nếu khách chọn sẵn voucher từ ví.
 */
public record BookingRequest(

        @NotBlank(message = "Loại hành trình không được để trống")
        @Pattern(regexp = "(?i)ONE_WAY|ROUND_TRIP", message = "Loại hành trình chỉ nhận ONE_WAY hoặc ROUND_TRIP")
        String tripType,

        @NotEmpty(message = "Danh sách chặng bay không được để trống")
        @Size(min = 1, max = 2, message = "Đơn đặt vé chỉ gồm 1 đến 2 chặng bay")
        @Valid
        List<SegmentItem> segments,

        @Valid
        @Size(max = 9, message = "Một đơn đặt vé tối đa 9 hành khách")
        List<PassengerItem> passengers,

        /** ID của dòng user_vouchers trong ví của khách (tùy chọn). */
        Long userVoucherId
) {
    public record SegmentItem(
            @NotNull(message = "Chuyến bay của chặng không được để trống")
            Long flightId,

            @NotNull(message = "Hạng vé của chặng không được để trống")
            Long fareClassId,

            Integer segmentOrder
    ) {
    }

    public record PassengerItem(
            @NotBlank(message = "Họ tên hành khách không được để trống")
            @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
            String fullName,

            @NotBlank(message = "Giới tính hành khách không được để trống")
            @Pattern(regexp = "(?i)MALE|FEMALE|OTHER", message = "Giới tính chỉ nhận MALE, FEMALE hoặc OTHER")
            String gender,

            @NotNull(message = "Ngày sinh hành khách không được để trống")
            LocalDate birthDate,

            @NotBlank(message = "Loại hành khách không được để trống")
            @Pattern(regexp = "(?i)ADULT|CHILD|INFANT", message = "Loại hành khách chỉ nhận ADULT, CHILD hoặc INFANT")
            String passengerType,

            @Size(max = 50, message = "Số giấy tờ tối đa 50 ký tự")
            String passportNumber,

            @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
            String phone,

            @Email(message = "Email không đúng định dạng")
            @Size(max = 255, message = "Email tối đa 255 ký tự")
            String email
    ) {
    }
}

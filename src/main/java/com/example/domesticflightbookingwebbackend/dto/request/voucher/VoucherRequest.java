package com.example.domesticflightbookingwebbackend.dto.request.voucher;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Payload POST/PUT /vouchers (mục 2.8 API v0.2). */
public record VoucherRequest(

        @NotBlank(message = "Mã voucher không được để trống")
        @Size(max = 50, message = "Mã voucher tối đa 50 ký tự")
        String voucherCode,

        @NotBlank(message = "Tên voucher không được để trống")
        @Size(max = 150, message = "Tên voucher tối đa 150 ký tự")
        String voucherName,

        @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
        String description,

        @NotBlank(message = "Cách phát voucher không được để trống")
        @Pattern(regexp = "GLOBAL|ASSIGNED", message = "distribution_type chỉ nhận GLOBAL hoặc ASSIGNED")
        String distributionType,

        @NotBlank(message = "Loại giảm giá không được để trống")
        @Pattern(regexp = "PERCENT|AMOUNT", message = "Loại giảm chỉ nhận PERCENT hoặc AMOUNT")
        String discountType,

        @NotNull(message = "Giá trị giảm không được để trống")
        BigDecimal discountValue,

        BigDecimal maxDiscountAmount,
        BigDecimal minOrderAmount,

        @Pattern(regexp = "ANY|ONE_WAY|ROUND_TRIP", message = "Loại hành trình không hợp lệ")
        String applicableTripType,

        @NotNull(message = "Ngày bắt đầu không được để trống")
        LocalDate startDate,

        @NotNull(message = "Ngày kết thúc không được để trống")
        LocalDate endDate,

        /** Chỉ áp cho GLOBAL; null = không giới hạn. */
        Integer claimLimit,

        Boolean isActive,

        List<PromotionScopeItem> scopes,
        List<Long> excludedFlightIds
) {
    /** Cùng cấu trúc phạm vi như promotion (voucher_scopes). */
    public record PromotionScopeItem(
            String targetType,
            Long airlineId,
            Long fareClassId,
            Long flightId,
            Long departureAirportId,
            Long arrivalAirportId
    ) {
    }
}

package com.example.domesticflightbookingwebbackend.dto.request.promotion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Payload POST/PUT /promotions (mục 2.7 API v0.2). */
public record PromotionRequest(

        @NotBlank(message = "Mã khuyến mãi không được để trống")
        @Size(max = 50, message = "Mã khuyến mãi tối đa 50 ký tự")
        String promotionCode,

        @NotBlank(message = "Tên khuyến mãi không được để trống")
        @Size(max = 150, message = "Tên khuyến mãi tối đa 150 ký tự")
        String promotionName,

        @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
        String description,

        @Size(max = 500, message = "Đường dẫn ảnh tối đa 500 ký tự")
        String imageUrl,

        @Size(max = 30, message = "Nhãn tối đa 30 ký tự")
        String badgeLabel,

        @NotBlank(message = "Loại giảm giá không được để trống")
        @Pattern(regexp = "PERCENT|AMOUNT", message = "Loại giảm chỉ nhận PERCENT hoặc AMOUNT")
        String discountType,

        @NotNull(message = "Giá trị giảm không được để trống")
        BigDecimal discountValue,

        BigDecimal maxDiscountAmount,
        BigDecimal minFareAmount,

        @Pattern(regexp = "ANY|ONE_WAY|ROUND_TRIP", message = "Loại hành trình không hợp lệ")
        String applicableTripType,

        @NotNull(message = "Ngày bắt đầu không được để trống")
        LocalDate startDate,

        @NotNull(message = "Ngày kết thúc không được để trống")
        LocalDate endDate,

        Boolean isActive,

        /** Phạm vi áp dụng; rỗng = áp cho tất cả chuyến bay. */
        List<ScopeItem> scopes,

        /** Chuyến bay bị loại trừ (loại trừ luôn thắng phạm vi). */
        List<Long> excludedFlightIds
) {
    /** Một dòng phạm vi: target_type + đúng FK tương ứng. */
    public record ScopeItem(
            String targetType,
            Long airlineId,
            Long fareClassId,
            Long flightId,
            Long departureAirportId,
            Long arrivalAirportId
    ) {
    }
}

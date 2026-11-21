package com.example.domesticflightbookingwebbackend.dto.request.masterdata;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payload POST/PUT /admin/fare-rules.
 *
 * <p>{@code version} KHÔNG nhận từ client — backend tự tăng theo hạng vé.
 * {@code fare_class_id} chỉ dùng khi tạo.
 */
public record FareRuleRequest(

        @NotNull(message = "Hạng vé không được để trống")
        Long fareClassId,

        @NotNull(message = "Phải chọn có được hoàn vé hay không")
        Boolean isRefundable,

        @NotNull(message = "Số giờ tối thiểu trước giờ bay không được để trống")
        @Min(value = 0, message = "Số giờ tối thiểu không được âm")
        @Max(value = 720, message = "Số giờ tối thiểu tối đa 720 giờ (30 ngày)")
        Integer refundMinHoursBeforeDeparture,

        @NotNull(message = "Phần trăm hoàn không được để trống")
        @DecimalMin(value = "0", message = "Phần trăm hoàn không được âm")
        @DecimalMax(value = "100", message = "Phần trăm hoàn tối đa 100")
        BigDecimal refundPercent,

        @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
        String description,

        /** Bỏ trống = có hiệu lực ngay. */
        LocalDateTime effectiveFrom,

        @Pattern(regexp = "(?i)ACTIVE|INACTIVE", message = "Trạng thái chỉ nhận ACTIVE hoặc INACTIVE")
        String status
) {
}

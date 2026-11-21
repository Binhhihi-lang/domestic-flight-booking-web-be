package com.example.domesticflightbookingwebbackend.dto.request.masterdata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload POST/PUT /admin/fare-classes.
 *
 * <p>{@code fare_class_code} gửi phần hạng (ECON_SAVER) hoặc mã đầy đủ
 * (VN_ECON_SAVER) đều được — backend tự thêm tiền tố hãng (V18).
 * {@code airline_id} và {@code fare_class_code} chỉ dùng khi tạo; khi sửa
 * bị bỏ qua vì booking/khuyến mãi đã tham chiếu theo mã.
 */
public record FareClassRequest(

        @NotNull(message = "Hãng áp dụng không được để trống")
        Long airlineId,

        @NotBlank(message = "Mã hạng vé không được để trống")
        @Size(max = 19, message = "Mã hạng vé tối đa 19 ký tự (chưa tính tiền tố hãng)")
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "Mã hạng vé chỉ gồm chữ, số và dấu gạch dưới")
        String fareClassCode,

        @NotBlank(message = "Tên hạng vé không được để trống")
        @Size(max = 100, message = "Tên hạng vé tối đa 100 ký tự")
        String fareClassName,

        @NotBlank(message = "Hạng dịch vụ không được để trống")
        @Pattern(
                regexp = "ECONOMY|PREMIUM_ECONOMY|BUSINESS",
                message = "Hạng dịch vụ chỉ nhận ECONOMY, PREMIUM_ECONOMY hoặc BUSINESS")
        String seatClass,

        /** ACTIVE | INACTIVE — không gửi thì mặc định ACTIVE. */
        @Pattern(regexp = "(?i)ACTIVE|INACTIVE", message = "Trạng thái chỉ nhận ACTIVE hoặc INACTIVE")
        String status
) {
}

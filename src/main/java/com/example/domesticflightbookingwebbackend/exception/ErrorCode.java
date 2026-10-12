package com.example.domesticflightbookingwebbackend.exception;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

/**
 * Mã lỗi nghiệp vụ (mục 1.4 — API Design v0.2).
 *
 * <p>9 mã gốc 1000–1008 + 9999 là hợp đồng cố định với frontend.
 * {@link #VALIDATION_ERROR} được trả kèm mảng {@code {field, message}} trong
 * {@code data}; các mã khác trả {@code data: null}.
 */
@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ErrorCode {

    SUCCESS(1000, "Success", HttpStatus.OK),
    VALIDATION_ERROR(1001, "Dữ liệu không hợp lệ", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(1002, "Chưa xác thực hoặc phiên đăng nhập đã hết hạn", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(1003, "Bạn không có quyền truy cập chức năng này", HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(1004, "Không tìm thấy tài nguyên", HttpStatus.NOT_FOUND),
    DUPLICATE_RESOURCE(1005, "Dữ liệu đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
    BUSINESS_RULE_VIOLATION(1006, "Vi phạm quy tắc nghiệp vụ", HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(1007, "Email hoặc mật khẩu không đúng", HttpStatus.BAD_REQUEST),
    ACCOUNT_INACTIVE(1008, "Tài khoản đã bị vô hiệu hóa", HttpStatus.FORBIDDEN),
    INTERNAL_ERROR(9999, "Lỗi hệ thống, vui lòng thử lại sau", HttpStatus.INTERNAL_SERVER_ERROR);

    int code;
    String message;
    HttpStatus httpStatus;
}

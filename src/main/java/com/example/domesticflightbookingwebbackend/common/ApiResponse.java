package com.example.domesticflightbookingwebbackend.common;

import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import lombok.Getter;
import lombok.Setter;

/**
 * Envelope bọc MỌI response của API (mục 1.3 — API Design v0.2).
 *
 * Lưu ý: KHÔNG dùng @JsonInclude(NON_NULL) — hợp đồng yêu cầu trường null
 * luôn có mặt trong response để client không phải phân biệt "không có field"
 * và "field rỗng" (mục 1.2).
 */
@Getter
@Setter
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), message, data);
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, T data) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), data);
    }
}

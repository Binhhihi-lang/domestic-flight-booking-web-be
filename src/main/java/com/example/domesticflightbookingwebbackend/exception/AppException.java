package com.example.domesticflightbookingwebbackend.exception;

import lombok.Getter;

/**
 * Lỗi nghiệp vụ chủ động ném ra từ tầng service.
 *
 * <p>HTTP status lấy theo {@link ErrorCode}; có thể ghi đè message của enum
 * để nêu rõ ngữ cảnh (ví dụ: "Hãng bay VN còn 3 chuyến đang hoạt động").
 */
@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}

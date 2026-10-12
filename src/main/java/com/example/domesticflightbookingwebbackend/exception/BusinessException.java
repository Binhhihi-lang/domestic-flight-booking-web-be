package com.example.domesticflightbookingwebbackend.exception;

/** Vi phạm quy tắc nghiệp vụ (mã 1006) — ví dụ end_date < start_date, hãng còn chuyến hoạt động. */
public class BusinessException extends AppException {

    public BusinessException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}

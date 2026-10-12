package com.example.domesticflightbookingwebbackend.exception;

/** Vi phạm ràng buộc UNIQUE (mã 1005) — email, airline_code, city_code... */
public class DuplicateResourceException extends AppException {

    public DuplicateResourceException(String message) {
        super(ErrorCode.DUPLICATE_RESOURCE, message);
    }
}

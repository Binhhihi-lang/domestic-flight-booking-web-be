package com.example.domesticflightbookingwebbackend.exception;

/** Không tìm thấy tài nguyên (mã 1004). */
public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}

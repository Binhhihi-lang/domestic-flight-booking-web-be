package com.example.domesticflightbookingwebbackend.exception;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Chuyển mọi exception thành envelope {@link ApiResponse} theo hợp đồng
 * API Design v0.2 mục 1.4. Không bao giờ lộ stack trace ra client.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Lỗi nghiệp vụ chủ động ném từ service (AppException và các lớp con). */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        String message = (ex.getMessage() != null && !ex.getMessage().isBlank())
                ? ex.getMessage()
                : errorCode.getMessage();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode, message));
    }

    /**
     * Lỗi validate (@Valid trên @RequestBody).
     * Trả mã 1001 với {@code data} là mảng {@code {field, message}} (mục 1.4).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<FieldErrorDetail>>> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldErrorDetail)
                .toList();
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, errors));
    }

    /** Tham số query thiếu hoặc sai kiểu (ví dụ page=abc). */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, "Tham số yêu cầu không hợp lệ"));
    }

    /** Body JSON sai định dạng / không đọc được. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("Unreadable body: {}", ex.getMessage());
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, "Nội dung yêu cầu không hợp lệ"));
    }

    /**
     * Vi phạm ràng buộc UNIQUE ở tầng DB — lưới an toàn khi service chưa
     * kiểm tra trùng. Trả 1005 thay vì 9999.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(ErrorCode.DUPLICATE_RESOURCE.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.DUPLICATE_RESOURCE));
    }

    /** Thiếu quyền (đã xác thực nhưng không đủ permission) → 1003. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(ErrorCode.ACCESS_DENIED.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.ACCESS_DENIED));
    }

    /** Chưa xác thực / token sai hoặc hết hạn → 1002. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(ErrorCode.UNAUTHENTICATED.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.UNAUTHENTICATED));
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNoHandler(Exception ex) {
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "Đường dẫn không tồn tại"));
    }

    /** Lưới cuối: mọi lỗi chưa phân loại → 9999, log đầy đủ ở server, không lộ ra client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUncaught(Exception ex) {
        log.error("Uncaught exception", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
    }

    private FieldErrorDetail toFieldErrorDetail(FieldError fieldError) {
        String message = fieldError.getDefaultMessage();
        return new FieldErrorDetail(fieldError.getField(), message == null ? "Không hợp lệ" : message);
    }

    /** Một lỗi validate theo field — serialize ra {@code {"field": ..., "message": ...}}. */
    public record FieldErrorDetail(String field, String message) {
    }
}

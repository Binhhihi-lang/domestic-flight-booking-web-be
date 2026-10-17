package com.example.domesticflightbookingwebbackend.config;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Bắt lỗi 403
 * Lỗi phân quyền ở tầng filter chain (URL matcher) → 1003.
 * Token hợp lệ nhưng authorities RỖNG (user bị khóa/xóa mềm) cũng trả 1003 để
 * client xóa phiên và đăng nhập lại.
 */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean revoked = authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().isEmpty();

        ErrorCode errorCode = revoked ? ErrorCode.UNAUTHENTICATED : ErrorCode.ACCESS_DENIED;

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(errorCode)));
        response.flushBuffer();
    }
}

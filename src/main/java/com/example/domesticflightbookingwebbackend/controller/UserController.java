package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.dto.request.user.CreateUserRequest;
import com.example.domesticflightbookingwebbackend.dto.request.user.UpdateUserRequest;
import com.example.domesticflightbookingwebbackend.dto.response.auth.UserResponse;
import com.example.domesticflightbookingwebbackend.dto.response.user.ResetPasswordResponse;
import com.example.domesticflightbookingwebbackend.security.SecurityUtils;
import com.example.domesticflightbookingwebbackend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** User Management cho ADMIN (mục 2.3 API v0.2). */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    public ApiResponse<PageData<UserResponse>> list(
            ListQuery query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role) {
        return ApiResponse.success(userService.list(query, status, role));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ_DETAIL')")
    public ApiResponse<UserResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(userService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo người dùng thành công", userService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ApiResponse<UserResponse> update(@PathVariable Long id,
                                            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success("Cập nhật người dùng thành công",
                userService.update(id, request, SecurityUtils.getCurrentUserId()));
    }

    /** Soft delete (USR-05): đặt is_active=false + is_deleted=true, giữ lịch sử. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.delete(id, SecurityUtils.getCurrentUserId());
        return ApiResponse.success("Đã vô hiệu hóa người dùng", null);
    }

    /** Đặt lại mật khẩu → trả mật khẩu tạm cho ADMIN (open item #4). */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ApiResponse<ResetPasswordResponse> resetPassword(@PathVariable Long id) {
        String temp = userService.resetPassword(id);
        return ApiResponse.success("Đã đặt lại mật khẩu",
                new ResetPasswordResponse(temp));
    }
}

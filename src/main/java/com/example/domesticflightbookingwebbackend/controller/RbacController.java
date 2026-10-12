package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.dto.response.rbac.PermissionResponse;
import com.example.domesticflightbookingwebbackend.dto.response.rbac.RoleResponse;
import com.example.domesticflightbookingwebbackend.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * RBAC read-only (mục 2.4 API v0.2, BR-RBAC-03). Cả hai endpoint dùng chung
 * quyền `ROLE_READ` — Permission Matrix v2.0 không có key PERMISSION_READ riêng.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RbacController {

    private final RbacService rbacService;

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    public ApiResponse<List<RoleResponse>> listRoles() {
        return ApiResponse.success(rbacService.listRoles());
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    public ApiResponse<List<PermissionResponse>> listPermissions(
            ListQuery query,
            @RequestParam(required = false) String module) {
        return ApiResponse.success(rbacService.listPermissions(query, module));
    }
}

package com.example.domesticflightbookingwebbackend.dto.response.rbac;

/** Một permission trong catalog (GET /permissions — có search/filter/sort). */
public record PermissionResponse(Long id, String permissionKey, String description, String module) {
}

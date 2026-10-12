package com.example.domesticflightbookingwebbackend.dto.response.rbac;

/** Một role trong catalog (GET /roles — mảng trần, không phân trang). */
public record RoleResponse(Long id, String name, String description) {
}

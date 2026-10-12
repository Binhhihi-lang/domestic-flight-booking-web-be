package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.dto.response.rbac.PermissionResponse;
import com.example.domesticflightbookingwebbackend.dto.response.rbac.RoleResponse;
import com.example.domesticflightbookingwebbackend.entity.Permission;
import com.example.domesticflightbookingwebbackend.repository.PermissionRepository;
import com.example.domesticflightbookingwebbackend.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * RBAC read-only (mục 2.4 API v0.2, BR-RBAC-03): role và permission là catalog
 * cố định, seed bằng migration, KHÔNG có POST/PUT/DELETE.
 */
@Service
@RequiredArgsConstructor
public class RbacService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    /** GET /roles — trả MẢNG TRẦN, không phân trang (BR-LIST-05). */
    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll(Sort.by("id")).stream()
                .map(r -> new RoleResponse(r.getId(), r.getName(), r.getDescription()))
                .toList();
    }

    /**
     * GET /permissions — hỗ trợ keyword (permission_key/description), filter
     * module, sort (mặc định permission_key,asc). Trả mảng trần theo BR-LIST-05.
     */
    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions(ListQuery query, String module) {
        String keyword = query.getKeywordOrNull();
        Sort sort = resolveSort(query);

        return permissionRepository.findAll(sort).stream()
                .filter(p -> module == null || module.isBlank()
                        || module.equalsIgnoreCase(p.getModule()))
                .filter(p -> keyword == null
                        || p.getPermissionKey().toLowerCase().contains(keyword.toLowerCase())
                        || (p.getDescription() != null
                            && p.getDescription().toLowerCase().contains(keyword.toLowerCase())))
                .map(p -> new PermissionResponse(
                        p.getId(), p.getPermissionKey(), p.getDescription(), p.getModule()))
                .toList();
    }

    /** Sort chỉ nhận permission_key (mặc định asc); field khác → dùng mặc định. */
    private Sort resolveSort(ListQuery query) {
        if (!query.isSortAllowed("permission_key")) {
            return Sort.by(Sort.Direction.ASC, "permissionKey");
        }
        Sort.Direction direction = "desc".equals(query.getSortDirection())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, "permissionKey");
    }
}

package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.common.PageData;
import com.example.domesticflightbookingwebbackend.common.PageMapper;
import com.example.domesticflightbookingwebbackend.dto.request.user.CreateUserRequest;
import com.example.domesticflightbookingwebbackend.dto.request.user.UpdateUserRequest;
import com.example.domesticflightbookingwebbackend.dto.response.auth.UserResponse;
import com.example.domesticflightbookingwebbackend.entity.Role;
import com.example.domesticflightbookingwebbackend.entity.User;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.DuplicateResourceException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.mapper.UserMapper;
import com.example.domesticflightbookingwebbackend.repository.RoleRepository;
import com.example.domesticflightbookingwebbackend.repository.UserRepository;
import com.example.domesticflightbookingwebbackend.repository.redis.RefreshTokenRepository;
import com.example.domesticflightbookingwebbackend.security.UserDetailsServiceImpl;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** User Management cho ADMIN (mục 2.3 API v0.2, USR-01 → USR-05). */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    /** Các role hợp lệ — role cố định MVP (BR-RBAC-03). */
    private static final List<String> VALID_ROLES = List.of("CUSTOMER", "STAFF", "ADMIN");
    /** Whitelist sort theo tên field HỢP ĐỒNG API (snake_case). */
    private static final String[] SORT_WHITELIST = {"email", "full_name", "created_at"};

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final UserDetailsServiceImpl userDetailsService;
    private final RefreshTokenRepository refreshTokenRepository;

    // ================== LIST / DETAIL ==================

    @Transactional(readOnly = true)
    public PageData<UserResponse> list(ListQuery query, String status, String role) {
        Pageable pageable = PageMapper.toPageable(query, "createdAt", SORT_WHITELIST);
        Page<User> page = userRepository.findAll(buildFilter(query, status, role), pageable);
        return PageMapper.toPageData(page, userMapper::toResponse);
    }

    private Specification<User> buildFilter(ListQuery query, String status, String role) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // BR-LIST-04: không bao giờ lộ user đã xóa mềm
            predicates.add(cb.isFalse(root.get("isDeleted")));

            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("isActive"), "ACTIVE".equalsIgnoreCase(status)));
            }
            if (role != null && !role.isBlank()) {
                predicates.add(cb.equal(root.join("role").get("name"), role.toUpperCase()));
            }

            String keyword = query.getKeywordOrNull();
            if (keyword != null) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("fullName")), like),
                        cb.like(cb.lower(root.get("phone")), like)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return userMapper.toResponse(findOrThrow(id));
    }

    // ================== CREATE ==================

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailAndIsDeletedFalse(email)) {
            throw new DuplicateResourceException("Email đã được sử dụng");
        }

        Role role = resolveRole(request.roles());

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone());
        user.setRole(role);
        user.setIsActive(!"INACTIVE".equalsIgnoreCase(request.status()));
        user.setIsDeleted(false);
        userRepository.save(user);

        return userMapper.toResponse(user);
    }

    // ================== UPDATE ==================

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, Long currentAdminId) {
        User user = findOrThrow(id);
        Role newRole = resolveRole(request.roles());
        boolean newActive = !"INACTIVE".equalsIgnoreCase(request.status());

        // Chặn leo thang đặc quyền: ADMIN không tự hạ quyền nếu là ADMIN ACTIVE cuối cùng
        boolean losingAdmin = "ADMIN".equals(user.getRole().getName())
                && (!"ADMIN".equals(newRole.getName()) || !newActive);
        if (losingAdmin && id.equals(currentAdminId) && countActiveAdmins() <= 1) {
            throw new BusinessException(
                    "Không thể tự hạ quyền: đây là tài khoản ADMIN đang hoạt động cuối cùng");
        }

        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone().isBlank() ? null : request.phone().trim());
        }
        user.setRole(newRole);
        user.setIsActive(newActive);
        userRepository.save(user);

        // Đổi role hoặc khóa tài khoản → quyền phải có hiệu lực ngay
        userDetailsService.evictAuthorities(id);
        if (!newActive) {
            revokeAllRefreshTokens(id);
        }

        return userMapper.toResponse(user);
    }

    // ================== DELETE (soft) ==================

    @Transactional
    public void delete(Long id) {
        User user = findOrThrow(id);

        // USR-05 + ràng buộc 14/10: ADMIN không xóa ADMIN (chỉ hạ quyền nếu còn ADMIN khác)
        if ("ADMIN".equals(user.getRole().getName())) {
            log.warn("Từ chối xóa tài khoản ADMIN id={}", id);
            throw new BusinessException("Không thể xóa tài khoản ADMIN");
        }

        // USR-05: soft delete — đặt is_active=false + is_deleted=true, giữ lịch sử
        user.setIsActive(false);
        user.setIsDeleted(true);
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);

        userDetailsService.evictAuthorities(id);
        revokeAllRefreshTokens(id);
    }

    // ================== RESET PASSWORD (ADMIN) ==================

    /**
     * Đặt lại mật khẩu thành mật khẩu tạm ngẫu nhiên và trả về cho ADMIN
     * (open item #4 — không có mail server).
     */
    @Transactional
    public String resetPassword(Long id) {
        User user = findOrThrow(id);
        String temporaryPassword = generateTemporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        userRepository.save(user);

        revokeAllRefreshTokens(id);
        userDetailsService.evictAuthorities(id);
        return temporaryPassword;
    }

    // ================== Helpers ==================

    private User findOrThrow(Long id) {
        return userRepository.findById(id)
                .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));
    }

    /** Chỉ nhận role trong danh sách cố định; giá trị khác → 1001. */
    private Role resolveRole(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Phải chọn role");
        }
        // MVP: mỗi user đúng 1 role → lấy phần tử đầu
        String roleName = roles.get(0).toUpperCase();
        if (!VALID_ROLES.contains(roleName)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Role không hợp lệ: chỉ nhận " + String.join(", ", VALID_ROLES));
        }
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new BusinessException("Role chưa được seed: " + roleName));
    }

    private long countActiveAdmins() {
        return userRepository.findAll().stream()
                .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .filter(u -> "ADMIN".equals(u.getRole().getName()))
                .count();
    }

    private void revokeAllRefreshTokens(Long userId) {
        refreshTokenRepository.deleteAll(refreshTokenRepository.findByUserId(String.valueOf(userId)));
    }

    private String generateTemporaryPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789@#";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}

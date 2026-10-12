package com.example.domesticflightbookingwebbackend.security;

import com.example.domesticflightbookingwebbackend.entity.Permission;
import com.example.domesticflightbookingwebbackend.entity.User;
import com.example.domesticflightbookingwebbackend.entity.redis.CachedAuthorities;
import com.example.domesticflightbookingwebbackend.repository.UserRepository;
import com.example.domesticflightbookingwebbackend.repository.redis.CachedAuthoritiesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Nguồn authorities cho một user: đọc cache Redis trước, cache miss thì tính
 * từ DB rồi lưu lại (TTL ngắn).
 *
 * <p>Quyền của user bị KHÓA hoặc đã xóa mềm → trả danh sách rỗng (bị từ chối
 * toàn bộ). Kết quả rỗng KHÔNG được ghi cache để khi admin kích hoạt lại,
 * quyền được tính lại từ DB thay vì kẹt cache rỗng.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl {

    /** TTL của cache authorities (giây) — lưới an toàn phòng khi quên evict. */
    private static final long CACHE_TTL_SECONDS = 300L;

    private final UserRepository userRepository;
    private final CachedAuthoritiesRepository cachedAuthoritiesRepository;

    @Transactional(readOnly = true)
    public List<GrantedAuthority> getAuthorities(Long userId) {
        String cacheKey = String.valueOf(userId);

        CachedAuthorities cached = cachedAuthoritiesRepository.findById(cacheKey).orElse(null);
        if (cached != null) {
            return cached.getAuthorities().stream()
                    .map(SimpleGrantedAuthority::new)
                    .map(GrantedAuthority.class::cast)
                    .toList();
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted()) || !Boolean.TRUE.equals(user.getIsActive())) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        if (user.getRole() != null) {
            names.add("ROLE_" + user.getRole().getName());
            if (user.getRole().getPermissions() != null) {
                for (Permission permission : user.getRole().getPermissions()) {
                    names.add(permission.getPermissionKey());
                }
            }
        }

        cachedAuthoritiesRepository.save(CachedAuthorities.builder()
                .userId(cacheKey)
                .authorities(names)
                .ttl(CACHE_TTL_SECONDS)
                .build());

        return names.stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    /** Xóa cache quyền của một user — gọi khi đổi role, khóa, hoặc xóa user. */
    public void evictAuthorities(Long userId) {
        cachedAuthoritiesRepository.deleteById(String.valueOf(userId));
    }
}

package com.example.domesticflightbookingwebbackend.entity.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.io.Serializable;
import java.util.List;

/**
 * Cache danh sách quyền (authorities) của một user, key = userId.
 *
 * <p>Converter xác thực đọc cache này trước (0 query DB khi hit). TTL ngắn là
 * lưới an toàn phòng khi quên evict; thu hồi NGAY thực hiện bằng cách xóa key
 * khi role/user thay đổi.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "USER_AUTHORITIES")
public class CachedAuthorities implements Serializable {

    @Id
    private String userId;

    /** Ví dụ {@code ["ROLE_ADMIN", "USER_READ", "AIRLINE_CREATE"]}. */
    private List<String> authorities;

    @TimeToLive
    private Long ttl;
}

package com.example.domesticflightbookingwebbackend.entity.redis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serializable;

/**
 * Refresh token đang hiệu lực (whitelist). Chỉ token có trong đây mới đổi được
 * access token mới. Key = claim {@code jti} của refresh token.
 *
 * <p>{@code userId} được đánh index để thu hồi TOÀN BỘ refresh token của một
 * user (đổi mật khẩu, khóa tài khoản).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "REFRESH_TOKEN")
public class RefreshToken implements Serializable {

    @Id
    private String id;

    @Indexed
    private String userId;

    @TimeToLive
    private Long ttl;
}

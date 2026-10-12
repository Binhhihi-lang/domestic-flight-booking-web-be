package com.example.domesticflightbookingwebbackend.entity.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serializable;

/**
 * Token đặt lại mật khẩu (quên mật khẩu), TTL 15 phút.
 *
 * <p>Lưu ở Redis thay vì bảng DB (quyết định open item #2 kế hoạch BE): không
 * cần migration, token tự hết hạn. Đánh đổi: Redis restart thì token mất, user
 * bấm "quên mật khẩu" lại — chấp nhận được.
 *
 * <p>Key = token gửi cho user (UUID), value giữ userId.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "PASSWORD_RESET_TOKEN")
public class PasswordResetToken implements Serializable {

    @Id
    private String id;

    @Indexed
    private String userId;

    @TimeToLive
    private Long ttl;
}

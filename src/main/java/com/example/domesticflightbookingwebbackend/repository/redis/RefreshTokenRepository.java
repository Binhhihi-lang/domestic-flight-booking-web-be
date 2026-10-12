package com.example.domesticflightbookingwebbackend.repository.redis;

import com.example.domesticflightbookingwebbackend.entity.redis.RefreshToken;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {

    /** Thu hồi toàn bộ refresh token của một user (đổi mật khẩu, khóa tài khoản). */
    List<RefreshToken> findByUserId(String userId);
}

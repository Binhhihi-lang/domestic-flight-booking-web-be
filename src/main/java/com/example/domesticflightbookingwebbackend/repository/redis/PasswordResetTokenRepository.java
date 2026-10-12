package com.example.domesticflightbookingwebbackend.repository.redis;

import com.example.domesticflightbookingwebbackend.entity.redis.PasswordResetToken;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface PasswordResetTokenRepository extends CrudRepository<PasswordResetToken, String> {

    /** Thu hồi mọi token reset cũ của một user trước khi phát token mới. */
    List<PasswordResetToken> findByUserId(String userId);
}

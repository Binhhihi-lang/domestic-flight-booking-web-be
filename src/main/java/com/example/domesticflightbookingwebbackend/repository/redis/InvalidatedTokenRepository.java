package com.example.domesticflightbookingwebbackend.repository.redis;

import com.example.domesticflightbookingwebbackend.entity.redis.InvalidatedToken;
import org.springframework.data.repository.CrudRepository;

/** Redis repository — dùng CrudRepository, KHÔNG dùng JpaRepository. */
public interface InvalidatedTokenRepository extends CrudRepository<InvalidatedToken, String> {
}

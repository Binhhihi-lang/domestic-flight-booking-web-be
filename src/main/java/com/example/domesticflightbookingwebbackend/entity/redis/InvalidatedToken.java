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

/**
 * Access token đã bị thu hồi (logout). Key = claim {@code jti} của token.
 * TTL = thời gian còn lại của token, Redis tự xóa khi hết hạn nên blacklist
 * không phình vô hạn.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "INVALIDATED_TOKEN")
public class InvalidatedToken implements Serializable {

    @Id
    private String id;

    @TimeToLive
    private Long ttl;
}

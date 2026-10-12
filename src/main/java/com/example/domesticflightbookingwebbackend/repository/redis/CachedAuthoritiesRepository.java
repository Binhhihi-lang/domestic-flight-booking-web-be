package com.example.domesticflightbookingwebbackend.repository.redis;

import com.example.domesticflightbookingwebbackend.entity.redis.CachedAuthorities;
import org.springframework.data.repository.CrudRepository;

public interface CachedAuthoritiesRepository extends CrudRepository<CachedAuthorities, String> {
}

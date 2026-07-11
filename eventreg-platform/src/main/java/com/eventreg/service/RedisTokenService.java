package com.eventreg.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Single-use, self-expiring tokens (email verification, password reset) backed by
 * Redis TTL. No scheduled cleanup job needed — Redis evicts the key automatically.
 */
@Service
@RequiredArgsConstructor
public class RedisTokenService {

    private final StringRedisTemplate redisTemplate;

    private static final String VERIFY_PREFIX = "verify:";
    private static final String RESET_PREFIX = "reset:";

    public String createVerificationToken(Long userId, long expiryMinutes) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(VERIFY_PREFIX + token, userId.toString(), Duration.ofMinutes(expiryMinutes));
        return token;
    }

    public Optional<Long> consumeVerificationToken(String token) {
        String key = VERIFY_PREFIX + token;
        String userId = redisTemplate.opsForValue().get(key);
        if (userId == null) return Optional.empty();
        redisTemplate.delete(key); // single-use
        return Optional.of(Long.parseLong(userId));
    }

    public String createPasswordResetToken(Long userId, long expiryMinutes) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(RESET_PREFIX + token, userId.toString(), Duration.ofMinutes(expiryMinutes));
        return token;
    }

    public Optional<Long> consumePasswordResetToken(String token) {
        String key = RESET_PREFIX + token;
        String userId = redisTemplate.opsForValue().get(key);
        if (userId == null) return Optional.empty();
        redisTemplate.delete(key); // single-use
        return Optional.of(Long.parseLong(userId));
    }
}

package com.urlcutter.service;

import com.urlcutter.model.UrlMapping;
import com.urlcutter.repository.UrlMappingRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UrlService {

    private static final Duration MAX_CACHE_TTL = Duration.ofHours(24);
    private static final int MAX_TTL_DAYS = 36_500;
    private static final int MAX_URL_LENGTH = 2_048;

    private final UrlMappingRepository repository;
    private final IdGeneratorService idGenerator;
    private final StringRedisTemplate redisTemplate;

    public UrlService(UrlMappingRepository repository, IdGeneratorService idGenerator,
                      StringRedisTemplate redisTemplate) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.redisTemplate = redisTemplate;
    }

    public String shortenUrl(String longUrl, String customAlias, Integer ttlDays) {
        String normalizedLongUrl = validateLongUrl(longUrl);
        if (ttlDays != null && (ttlDays < 1 || ttlDays > MAX_TTL_DAYS)) {
            throw new IllegalArgumentException("Expiration must be between 1 and " + MAX_TTL_DAYS + " days");
        }

        String shortCode;
        if (customAlias != null && !customAlias.isBlank()) {
            shortCode = validateCustomAlias(customAlias);
            if (repository.existsByShortCode(shortCode)) {
                throw new IllegalArgumentException("Custom alias already taken.");
            }
        } else {
            do {
                shortCode = idGenerator.generateShortCode();
            } while (repository.existsByShortCode(shortCode));
        }

        LocalDateTime expiresAt = ttlDays == null ? null : LocalDateTime.now().plusDays(ttlDays);
        repository.save(new UrlMapping(shortCode, normalizedLongUrl, expiresAt));
        cache(shortCode, normalizedLongUrl, expiresAt);
        return shortCode;
    }

    public Optional<String> getLongUrl(String shortCode) {
        if (shortCode == null || !shortCode.matches("[A-Za-z0-9_-]{1,20}")) {
            return Optional.empty();
        }

        String cacheKey = cacheKey(shortCode);
        try {
            String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
            if (cachedUrl != null) {
                return Optional.of(cachedUrl);
            }
        } catch (DataAccessException ignored) {
            // Redis is an optimization; continue with the database when it is unavailable.
        }

        Optional<UrlMapping> mapping = repository.findByShortCode(shortCode);
        if (mapping.isEmpty() || isExpired(mapping.get())) {
            deleteCache(cacheKey);
            return Optional.empty();
        }

        UrlMapping activeMapping = mapping.get();
        cache(shortCode, activeMapping.getLongUrl(), activeMapping.getExpiresAt());
        return Optional.of(activeMapping.getLongUrl());
    }

    @Async
    public void recordClickAsync(String shortCode) {
        repository.incrementClickCountIfActive(shortCode, LocalDateTime.now());
    }

    private String validateLongUrl(String longUrl) {
        if (longUrl == null || longUrl.isBlank()) {
            throw new IllegalArgumentException("URL is required");
        }
        String trimmedUrl = longUrl.trim();
        if (trimmedUrl.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("URL must be at most " + MAX_URL_LENGTH + " characters");
        }

        try {
            URI uri = URI.create(trimmedUrl);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null
                    || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("URL must be a valid http or https URL");
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("URL must be a valid http or https URL", exception);
        }
    }

    private String validateCustomAlias(String customAlias) {
        String alias = customAlias.trim();
        if (!alias.matches("[A-Za-z0-9_-]{1,20}")) {
            throw new IllegalArgumentException("Custom alias must be 1-20 characters using letters, numbers, '-' or '_'");
        }
        return alias;
    }

    private void cache(String shortCode, String longUrl, LocalDateTime expiresAt) {
        Duration ttl = MAX_CACHE_TTL;
        if (expiresAt != null) {
            ttl = Duration.between(LocalDateTime.now(), expiresAt);
            if (ttl.compareTo(MAX_CACHE_TTL) > 0) {
                ttl = MAX_CACHE_TTL;
            }
        }
        if (ttl.isZero() || ttl.isNegative()) {
            deleteCache(cacheKey(shortCode));
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey(shortCode), longUrl, ttl);
        } catch (DataAccessException ignored) {
            // The database remains the source of truth if Redis is unavailable.
        }
    }

    private void deleteCache(String cacheKey) {
        try {
            redisTemplate.delete(cacheKey);
        } catch (DataAccessException ignored) {
            // Expired or missing rows will still be rejected by the database lookup.
        }
    }

    private String cacheKey(String shortCode) {
        return "url:" + shortCode;
    }

    private boolean isExpired(UrlMapping mapping) {
        return mapping.getExpiresAt() != null && !mapping.getExpiresAt().isAfter(LocalDateTime.now());
    }
}

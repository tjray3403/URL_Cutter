package com.urlcutter.service;

import com.urlcutter.model.UrlMapping;
import com.urlcutter.repository.UrlMappingRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UrlService {

    private final UrlMappingRepository repository;
    private final IdGeneratorService idGenerator;
    private final StringRedisTemplate redisTemplate;

    public UrlService(UrlMappingRepository repository, IdGeneratorService idGenerator, StringRedisTemplate redisTemplate) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.redisTemplate = redisTemplate;
    }

    public String shortenUrl(String longUrl, String customAlias, Integer ttlDays) {
        String shortCode;

        if (customAlias != null && !customAlias.isBlank()) {
            if (repository.existsByShortCode(customAlias)) {
                throw new IllegalArgumentException("Custom alias already taken.");
            }
            shortCode = customAlias;
        } else {
            shortCode = idGenerator.generateShortCode();
        }

        LocalDateTime expiresAt = (ttlDays != null && ttlDays > 0) ? LocalDateTime.now().plusDays(ttlDays) : null;
        UrlMapping mapping = new UrlMapping(shortCode, longUrl, expiresAt);
        repository.save(mapping);

        // Cache in Redis (TTL: 24h default)
        redisTemplate.opsForValue().set("url:" + shortCode, longUrl, Duration.ofHours(24));

        return shortCode;
    }

    public Optional<String> getLongUrl(String shortCode) {
        // Cache-Aside Pattern
        String cachedUrl = redisTemplate.opsForValue().get("url:" + shortCode);
        if (cachedUrl != null) {
            return Optional.of(cachedUrl);
        }

        Optional<UrlMapping> mappingOpt = repository.findByShortCode(shortCode);
        if (mappingOpt.isPresent()) {
            UrlMapping mapping = mappingOpt.get();
            if (mapping.getExpiresAt() != null && mapping.getExpiresAt().isBefore(LocalDateTime.now())) {
                return Optional.empty(); // Expired
            }
            redisTemplate.opsForValue().set("url:" + shortCode, mapping.getLongUrl(), Duration.ofHours(24));
            return Optional.of(mapping.getLongUrl());
        }

        return Optional.empty();
    }

    @Async
    public void recordClickAsync(String shortCode) {
        repository.findByShortCode(shortCode).ifPresent(mapping -> {
            mapping.setClickCount(mapping.getClickCount() + 1);
            repository.save(mapping);
        });
    }
}
package com.urlcutter.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import com.urlcutter.model.UrlMapping;
import java.time.LocalDateTime;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {
    Optional<UrlMapping> findByShortCode(String shortCode);
    boolean existsByShortCode(String shortCode);

    @Modifying
    @Transactional
    @Query("update UrlMapping mapping set mapping.clickCount = mapping.clickCount + 1 " +
            "where mapping.shortCode = :shortCode and " +
            "(mapping.expiresAt is null or mapping.expiresAt > :now)")
    int incrementClickCountIfActive(@Param("shortCode") String shortCode, @Param("now") LocalDateTime now);
}

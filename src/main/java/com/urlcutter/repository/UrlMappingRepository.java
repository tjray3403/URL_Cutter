package com.urlcutter.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import com.urlcutter.model.UrlMapping;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {
    Optional<UrlMapping> findByShortCode(String shortCode);
    boolean existsByShortCode(String shortCode);
}
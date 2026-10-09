package com.urlcutter.model;

import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

import java.time.LocalDateTime;

@Entity
@Table(name = "url_mappings", indexes = {
    @Index(name = "idx_short_code", columnList = "shortCode", unique = true)
})
public class UrlMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String shortCode;

    @Column(nullable = false, length = 2048)
    private String longUrl;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime expiresAt;

    private long clickCount = 0;

    public UrlMapping() {}

    public UrlMapping(String shortCode, String longUrl, LocalDateTime expiresAt) {
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.expiresAt = expiresAt;
    }

    public Long getId() { 
        return id; 
    }
    
    public String getShortCode() { 
        return shortCode; 
    }
    
    public String getLongUrl() { 
        return longUrl; 
    }
    
    public LocalDateTime getCreatedAt() { 
        return createdAt; 
    }
    
    public LocalDateTime getExpiresAt() { 
        return expiresAt; 
    }
    
    public long getClickCount() { 
        return clickCount; 
    }

    public void setClickCount(long clickCount) { 
        this.clickCount = clickCount; 
    }
}

package com.urlcutter.controller;

import com.urlcutter.service.UrlService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    @PostMapping("/api/urls")
    public ResponseEntity<?> createShortUrl(@RequestBody Map<String, Object> payload) {
        String longUrl = (String) payload.get("longUrl");
        String customAlias = (String) payload.get("customAlias");
        Integer ttlDays = (Integer) payload.get("ttlDays");

        if (longUrl == null || longUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "URL is required"));
        }

        try {
            String shortCode = urlService.shortenUrl(longUrl, customAlias, ttlDays);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("shortCode", shortCode, "shortUrl", "http://localhost:8080/" + shortCode));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirectToLongUrl(@PathVariable String shortCode) {
        Optional<String> longUrlOpt = urlService.getLongUrl(shortCode);

        if (longUrlOpt.isPresent()) {
            urlService.recordClickAsync(shortCode);
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(longUrlOpt.get()));
            return new ResponseEntity<>(headers, HttpStatus.FOUND); // HTTP 302
        } else {
            return ResponseEntity.notFound().build(); // 404 Not Found
        }
    }
}
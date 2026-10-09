package com.urlcutter.controller;

import com.urlcutter.service.UrlService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

@RestController
@RequestMapping
public class UrlController {

    private final UrlService urlService;
    private final String publicBaseUrl;

    public UrlController(UrlService urlService,
                         @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.urlService = urlService;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @PostMapping("/api/urls")
    public ResponseEntity<ShortUrlResponse> createShortUrl(@Valid @RequestBody CreateShortUrlRequest request) {
        try {
            String shortCode = urlService.shortenUrl(request.longUrl(), request.customAlias(), request.ttlDays());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ShortUrlResponse(shortCode, publicBaseUrl + "/" + shortCode));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Short code is already in use.", exception);
        }
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirectToLongUrl(@PathVariable String shortCode) {
        return urlService.getLongUrl(shortCode)
                .map(longUrl -> {
                    urlService.recordClickAsync(shortCode);
                    return ResponseEntity.status(HttpStatus.FOUND)
                            .header(HttpHeaders.LOCATION, URI.create(longUrl).toASCIIString())
                            .<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record CreateShortUrlRequest(
            @NotBlank String longUrl,
            @Pattern(regexp = "|[A-Za-z0-9_-]{1,20}", message = "Custom alias must be 1-20 characters using letters, numbers, '-' or '_'")
            String customAlias,
            @Min(value = 1, message = "Expiration must be at least 1 day") Integer ttlDays) {}

    public record ShortUrlResponse(String shortCode, String shortUrl) {}
}

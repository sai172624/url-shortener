package com.example.urlshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlResponse {

    private String shortCode;
    private String shortUrl;
    private String originalUrl;
    private OffsetDateTime expiresAt;
    private OffsetDateTime createdAt;
    private boolean isActive;
}
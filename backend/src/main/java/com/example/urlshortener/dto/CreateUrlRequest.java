package com.example.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUrlRequest {

    @NotBlank(message = "originalUrl must not be blank")
    @Size(max = 2048, message = "originalUrl must not exceed 2048 characters")
    @Pattern(regexp = "^https?://.*", message = "originalUrl must be a valid HTTP or HTTPS URL")
    private String originalUrl;

    private OffsetDateTime expiresAt;
}
package com.example.urlshortener.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        ErrorResponse response = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Invalid request parameters",
                errors,
                request
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                errors.put(violation.getPropertyPath().toString(), violation.getMessage())
        );

        ErrorResponse response = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Invalid request parameters",
                errors,
                request
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(CustomExceptions.UrlNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUrlNotFound(
            CustomExceptions.UrlNotFoundException ex, HttpServletRequest request) {

        ErrorResponse response = buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "URL_NOT_FOUND",
                ex.getMessage(),
                null,
                request
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(CustomExceptions.UrlExpiredException.class)
    public ResponseEntity<ErrorResponse> handleUrlExpired(
            CustomExceptions.UrlExpiredException ex, HttpServletRequest request) {

        ErrorResponse response = buildErrorResponse(
                HttpStatus.GONE,
                "URL_EXPIRED",
                ex.getMessage(),
                null,
                request
        );
        return ResponseEntity.status(HttpStatus.GONE).body(response);
    }

    @ExceptionHandler(CustomExceptions.UrlDeactivatedException.class)
    public ResponseEntity<ErrorResponse> handleUrlDeactivated(
            CustomExceptions.UrlDeactivatedException ex, HttpServletRequest request) {

        ErrorResponse response = buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "URL_DEACTIVATED",
                ex.getMessage(),
                null,
                request
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(CustomExceptions.UrlAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleUrlAccessDenied(
            CustomExceptions.UrlAccessDeniedException ex, HttpServletRequest request) {

        ErrorResponse response = buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                ex.getMessage(),
                null,
                request
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(CustomExceptions.DuplicateShortCodeException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateShortCode(
            CustomExceptions.DuplicateShortCodeException ex, HttpServletRequest request) {

        ErrorResponse response = buildErrorResponse(
                HttpStatus.CONFLICT,
                "DUPLICATE_SHORT_CODE",
                ex.getMessage(),
                null,
                request
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(CustomExceptions.TicketRangeExhaustedException.class)
    public ResponseEntity<ErrorResponse> handleTicketRangeExhausted(
            CustomExceptions.TicketRangeExhaustedException ex, HttpServletRequest request) {

        ErrorResponse response = buildErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "TICKET_RANGE_EXHAUSTED",
                ex.getMessage(),
                null,
                request
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(CustomExceptions.InvalidUrlException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUrl(
            CustomExceptions.InvalidUrlException ex, HttpServletRequest request) {

        ErrorResponse response = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_URL",
                ex.getMessage(),
                null,
                request
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(CustomExceptions.RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(
            CustomExceptions.RateLimitExceededException ex, HttpServletRequest request) {

        ErrorResponse response = buildErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMIT_EXCEEDED",
                ex.getMessage(),
                null,
                request
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
    }

    @ExceptionHandler({BadCredentialsException.class, CustomExceptions.AuthenticationException.class})
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            RuntimeException ex, HttpServletRequest request) {

        ErrorResponse response = buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_FAILED",
                "Invalid credentials",
                null,
                request
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler({CustomExceptions.TokenExpiredException.class, CustomExceptions.TokenRevokedException.class})
    public ResponseEntity<ErrorResponse> handleTokenException(
            RuntimeException ex, HttpServletRequest request) {

        ErrorResponse response = buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "TOKEN_INVALID",
                ex.getMessage(),
                null,
                request
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        ErrorResponse response = buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "Insufficient permissions to access this resource",
                null,
                request
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationCredentialsNotFound(
            AuthenticationCredentialsNotFoundException ex, HttpServletRequest request) {

        ErrorResponse response = buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "Authentication required",
                null,
                request
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        ErrorResponse response = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "TYPE_MISMATCH",
                "Invalid parameter type: " + ex.getName(),
                null,
                request
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {

        ErrorResponse response = buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                null,
                request
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private ErrorResponse buildErrorResponse(HttpStatus status, String error, String message,
                                             Map<String, String> details, HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }

        return ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(request.getRequestURI())
                .requestId(requestId)
                .details(details)
                .build();
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ErrorResponse {
        private OffsetDateTime timestamp;
        private int status;
        private String error;
        private String message;
        private String path;
        private String requestId;
        private Map<String, String> details;
    }
}
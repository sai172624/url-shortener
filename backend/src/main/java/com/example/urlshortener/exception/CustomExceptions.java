package com.example.urlshortener.exception;

public class CustomExceptions {

    public static class UrlNotFoundException extends RuntimeException {
        public UrlNotFoundException(String shortCode) {
            super("Short URL not found: " + shortCode);
        }
    }

    public static class UrlExpiredException extends RuntimeException {
        public UrlExpiredException(String shortCode) {
            super("Short URL has expired: " + shortCode);
        }
    }

    public static class UrlDeactivatedException extends RuntimeException {
        public UrlDeactivatedException(String shortCode) {
            super("Short URL has been deactivated: " + shortCode);
        }
    }

    public static class UrlAccessDeniedException extends RuntimeException {
        public UrlAccessDeniedException(String message) {
            super(message);
        }
    }

    public static class DuplicateShortCodeException extends RuntimeException {
        public DuplicateShortCodeException(String shortCode) {
            super("Duplicate short code generated: " + shortCode);
        }
    }

    public static class TicketRangeExhaustedException extends RuntimeException {
        public TicketRangeExhaustedException(String message) {
            super(message);
        }
    }

    public static class InvalidUrlException extends RuntimeException {
        public InvalidUrlException(String message) {
            super(message);
        }
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }

    public static class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }
    }

    public static class TokenExpiredException extends RuntimeException {
        public TokenExpiredException(String message) {
            super(message);
        }
    }

    public static class TokenRevokedException extends RuntimeException {
        public TokenRevokedException(String message) {
            super(message);
        }
    }
}
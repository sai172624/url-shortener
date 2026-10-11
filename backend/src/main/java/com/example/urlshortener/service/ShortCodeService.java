package com.example.urlshortener.service;

import org.springframework.stereotype.Service;

@Service
public class ShortCodeService {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = 62;

    /**
     * Encodes a numeric ID to Base62 string.
     * Deterministic and reversible for the numeric ID domain.
     *
     * @param id the numeric ID to encode (must be >= 0)
     * @return Base62 encoded string
     */
    public String encode(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("ID must be non-negative");
        }
        if (id == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }

        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            int remainder = (int) (id % BASE);
            sb.append(ALPHABET.charAt(remainder));
            id /= BASE;
        }
        return sb.reverse().toString();
    }

    /**
     * Decodes a Base62 string back to numeric ID.
     *
     * @param code the Base62 encoded string
     * @return the decoded numeric ID
     * @throws IllegalArgumentException if the code contains invalid characters
     */
    public long decode(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("Code cannot be null or empty");
        }

        long result = 0;
        for (char c : code.toCharArray()) {
            int index = ALPHABET.indexOf(c);
            if (index == -1) {
                throw new IllegalArgumentException("Invalid character in short code: " + c);
            }
            result = result * BASE + index;
        }
        return result;
    }

    /**
     * Validates if a string is a valid Base62 code.
     *
     * @param code the string to validate
     * @return true if valid Base62, false otherwise
     */
    public boolean isValidCode(String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        for (char c : code.toCharArray()) {
            if (ALPHABET.indexOf(c) == -1) {
                return false;
            }
        }
        return true;
    }
}
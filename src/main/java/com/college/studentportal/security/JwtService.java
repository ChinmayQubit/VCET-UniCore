package com.college.studentportal.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Generate a JWT token with the given claims.
     * @param email   User email (becomes the subject)
     * @param role    "STUDENT" or "ADMIN"
     * @param userId  The database ID of the user
     */
    public String generateToken(String email, String role, Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(email)
                .claims(Map.of(
                        "role", role,
                        "userId", userId
                ))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validate and parse the JWT token. Returns the Claims if valid.
     * Throws JwtException if invalid/expired.
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getEmail(Claims claims) {
        return claims.getSubject();
    }

    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }

    public Long getUserId(Claims claims) {
        return claims.get("userId", Long.class);
    }

    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}

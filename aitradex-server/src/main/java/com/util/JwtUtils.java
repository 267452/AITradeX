package com.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JwtUtils {

    @Value("${jwt.secret:aitradex-jwt-secret-key-must-be-at-least-256-bits-long}")
    private String secret;

    @Value("${jwt.expire-in:604800}")
    private long expireIn;

    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expireIn * 1000);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);

        return Jwts.builder()
            .claims(claims)
            .subject(userId.toString())
            .issuedAt(now)
            .expiration(expireDate)
            .signWith(getSigningKey())
            .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    public boolean isTokenBlacklisted(String token) {
        return blacklist.containsKey(token);
    }

    public void invalidateToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        try {
            Claims claims = parseToken(token);
            long remainingTime = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remainingTime > 0) {
                blacklist.put(token, claims.getExpiration().getTime());
            }
        } catch (Exception e) {
        }
    }

    public boolean validateToken(String token) {
        if (this.isTokenBlacklisted(token)) {
            return false;
        }
        return !this.isTokenExpired(token);
    }

    public Long getExpireIn() {
        return expireIn;
    }
}

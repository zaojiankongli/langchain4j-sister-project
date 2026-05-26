package com.zjkl.common.util;

import com.zjkl.common.config.properties.AuthProperties;
import com.zjkl.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final AuthProperties authProperties;

    private volatile SecretKey cachedSigningKey;

    public Long getAccessTokenExpiration() {
        return authProperties.getAccessTokenExpiration();
    }

    public String generateAccessToken(User user) {
        return createToken(user.getId(), user.getEmail(), user.getUsername(), authProperties.getAccessTokenExpiration());
    }

    public String generateRefreshToken(User user) {
        return createToken(user.getId(), user.getEmail(), user.getUsername(), authProperties.getRefreshTokenExpiration());
    }

    public String parseAccessToken(String token) {
        return parseTokenSubject(token);
    }

    public long getAccessTokenRemainingTime(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getExpiration().getTime() - System.currentTimeMillis();
        } catch (Exception e) {
            return -1;
        }
    }

    public String parseRefreshToken(String token) {
        return parseTokenSubject(token);
    }

    public JwtParseResult parseAccessTokenWithRemaining(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remaining <= 0) {
                return new JwtParseResult(null, -1);
            }
            return new JwtParseResult(claims.getSubject(), remaining);
        } catch (Exception e) {
            return new JwtParseResult(null, -1);
        }
    }

    public record JwtParseResult(String userId, long remainingTimeMs) {}

    private String createToken(String userId, String email, String username, Long expiration) {
        return Jwts.builder()
            .subject(userId)
            .claim("email", email)
            .claim("username", username)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expiration))
            .signWith(getSigningKey())
            .compact();
    }

    private String parseTokenSubject(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

            if (claims.getExpiration().before(new Date())) {
                return null;
            }

            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    private SecretKey getSigningKey() {
        if (cachedSigningKey != null) {
            return cachedSigningKey;
        }
        synchronized (this) {
            if (cachedSigningKey != null) {
                return cachedSigningKey;
            }
            byte[] keyBytes = authProperties.getSecret().getBytes();
            if (keyBytes.length < 32) {
                byte[] padded = new byte[32];
                System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
                keyBytes = padded;
            }
            cachedSigningKey = Keys.hmacShaKeyFor(keyBytes);
            return cachedSigningKey;
        }
    }
}

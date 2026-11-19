package com.nopilot.aiservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    public JwtService(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
    public String extractEmail(String token) { return claims(token).getSubject(); }
    public boolean isTokenValid(String token) {
        try {
            Claims claims = claims(token);
            return claims.getSubject() != null && claims.getExpiration() != null
                    && claims.getExpiration().after(new Date());
        } catch (RuntimeException ex) { return false; }
    }
    private Claims claims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}

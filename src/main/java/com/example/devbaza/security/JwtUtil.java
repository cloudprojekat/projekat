package com.example.devbaza.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    // Nema default vrednosti — aplikacija neće startovati bez JWT_SECRET env varijable
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration.days:7}")
    private int expirationDays;

    /**
     * Provera pri pokretanju — secret mora biti minimum 32 karaktera.
     * Ako nije, aplikacija odmah pada sa jasnom greškom.
     * Bolje pada na startu nego da radi sa slabim ključem!
     */
    @PostConstruct
    public void validate() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET mora biti minimum 32 karaktera! " +
                            "Generiši ga: openssl rand -base64 48"
            );
        }
    }

    private SecretKey getKey() {
        // UTF-8 enkodovanje — konzistentno na svim platformama
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generiše JWT token sa korisničkim podacima
     * Token sadrži: id, tip, ime u claims-ima i email kao subject
     */
    public String generisiToken(Long korisnikId, String email, String tip, String ime) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id",  korisnikId);
        claims.put("tip", tip);
        claims.put("ime", ime);

        long expirationMs = (long) expirationDays * 24 * 60 * 60 * 1000;

        return Jwts.builder()
                .claims(claims)
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getKey())
                .compact();
    }

    /**
     * Proverava da li je token validan i nije istekao
     */
    public boolean jeValidan(String token) {
        if (token == null || token.isBlank()) return false;
        try {
            Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            // Token istekao — ne logujemo jer je to normalno
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            // Neispravan token — potencijalni napad, ne logujemo IP ovde
            return false;
        }
    }

    /**
     * Proverava da li je token istekao
     * Korisno za razlikovanje "istekao" vs "neispravan"
     */
    public boolean jeIstekao(String token) {
        try {
            getClaims(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getEmail(String token) {
        return getClaims(token).getSubject();
    }

    public Long getId(String token) {
        Object id = getClaims(token).get("id");
        if (id instanceof Integer) return ((Integer) id).longValue();
        if (id instanceof Long)    return (Long) id;
        return null;
    }

    public String getTip(String token) {
        return (String) getClaims(token).get("tip");
    }

    public String getIme(String token) {
        return (String) getClaims(token).get("ime");
    }

    /**
     * Izvlači token iz Authorization headera
     * Očekuje format: "Bearer <token>"
     */
    public String izvuciIzHeadera(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
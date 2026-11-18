package ru.dimangan.easy_ui.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;

public class JWT {
    public static final String HEADER_AUTHORIZATION = "Authorization";
    private final static SecretKey key = new SecretKeySpec("955a589a-76c6-4903-b16d-46de608ec1c3".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    public static String generateToken(UserDetails userDetails) {
        Calendar issuedAt = Calendar.getInstance();
        Calendar expiration = new Calendar.Builder().setInstant(issuedAt.getTime()).build();
        expiration.add(Calendar.HOUR, 4);
        return Jwts
                .builder()
                .subject(userDetails.getUsername())
                .issuedAt(issuedAt.getTime())
                .expiration(expiration.getTime())
                .signWith( key)
                .compact();
    }

    public static Claims extractClaims(String token) {
        return Jwts
                .parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

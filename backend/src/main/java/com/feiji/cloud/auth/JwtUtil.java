package com.feiji.cloud.auth;

import com.feiji.cloud.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private static final String CLAIM_WORKSPACE = "ws";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final long expireHours;

    public JwtUtil(AppProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.security().jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.expireHours = properties.security().jwtExpireHours();
    }

    public String issue(String username, Long workspaceId, Role role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_WORKSPACE, workspaceId)
                .claim(CLAIM_ROLE, role == null ? Role.OBSERVER.name() : role.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireHours * 3600_000))
                .signWith(key)
                .compact();
    }

    public LoginUser parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        Long workspaceId = claims.get(CLAIM_WORKSPACE, Number.class) == null
                ? 1L : claims.get(CLAIM_WORKSPACE, Number.class).longValue();
        return new LoginUser(claims.getSubject(), workspaceId, claims.get(CLAIM_ROLE, String.class));
    }
}

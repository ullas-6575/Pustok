package com.example.EPustok.identity;

import jakarta.servlet.http.*;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Service
public class JwtService {
    public static final String COOKIE = "PUSTOK_TOKEN";
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final JdbcTemplate jdbc;
    private final boolean secure;
    public JwtService(@Value("${JWT_SECRET}") String secret, @Value("${JWT_COOKIE_SECURE:false}") boolean secure, JdbcTemplate jdbc) {
        byte[] bytes = java.util.Base64.getDecoder().decode(secret);
        if (bytes.length < 32) throw new IllegalArgumentException("JWT_SECRET must contain at least 32 random bytes, Base64 encoded");
        var key = new SecretKeySpec(bytes, "HmacSHA256");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var nimbus = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        nimbus.setJwtValidator(JwtValidators.createDefaultWithIssuer("pustok"));
        decoder = nimbus;
        this.jdbc = jdbc; this.secure = secure;
    }
    public String issue(String email) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("pustok").subject(email).id(UUID.randomUUID().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(3600)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
    public Jwt validate(String token) {
        Jwt jwt = decoder.decode(token);
        if (jwt.getId() == null || jwt.getSubject() == null || jwt.getExpiresAt() == null ||
                !jwt.getExpiresAt().isAfter(Instant.now()) ||
                Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from identity_revoked_tokens where token_id=?)", Boolean.class, jwt.getId()))) {
            throw new BadJwtException("Invalid or revoked token");
        }
        return jwt;
    }
    public void revoke(String token) {
        Jwt jwt = decoder.decode(token);
        jdbc.update("insert into identity_revoked_tokens(token_id, expires_at) values (?, ?) on conflict do nothing",
                jwt.getId(), java.sql.Timestamp.from(jwt.getExpiresAt()));
    }
    /** Revokes the presented token when it is still valid, then clears the browser cookie. Used by browser and API logout. */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String token = extract(request);
        if (token != null) {
            try {
                revoke(token);
            } catch (JwtException ignored) {
                // Malformed, forged or already expired: nothing valid to revoke.
            }
        }
        cookie(response, "");
        SecurityContextHolder.clearContext();
    }
    /** Issues a new token and revokes the one presented with the request, so every login rotates credentials. */
    public String rotate(HttpServletRequest request, String email) {
        String previous = extract(request);
        if (previous != null) {
            try {
                revoke(previous);
            } catch (JwtException ignored) {
                // Nothing valid to rotate away from.
            }
        }
        return issue(email);
    }
    public String extract(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) return header.substring(7);
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) if (COOKIE.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
    public void cookie(HttpServletResponse response, String token) {
        response.addHeader("Set-Cookie", ResponseCookie.from(COOKIE, token).httpOnly(true).secure(secure)
                .sameSite("Lax").path("/").maxAge(token.isEmpty() ? 0 : 3600).build().toString());
    }
}

package com.example.EPustok.identity;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtServiceTest {
    @Test void expiredTokenIsRejected() {
        var key = new javax.crypto.spec.SecretKeySpec(new byte[32], "HmacSHA256");
        var encoder = new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key));
        var now = java.time.Instant.now();
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer("pustok").subject("reader@example.com").id("expired")
                .issuedAt(now.minusSeconds(7200)).expiresAt(now.minusSeconds(3600)).build();
        String expired = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), claims)).getTokenValue();
        assertThrows(JwtException.class, () -> tokens.validate(expired));
    }
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final JwtService tokens = new JwtService(java.util.Base64.getEncoder().encodeToString(new byte[32]),false,jdbc);
    @Test void signedTokenContainsSubjectAndExpiry() {
        String token=tokens.issue("reader@example.com");
        var jwt=tokens.validate(token);
        assertEquals("reader@example.com",jwt.getSubject());
        assertNotNull(jwt.getId());
        assertEquals(3600,java.time.Duration.between(jwt.getIssuedAt(),jwt.getExpiresAt()).toSeconds());
    }
    @Test void modifiedSignatureIsRejected() {
        String token=tokens.issue("reader@example.com");
        String[] parts=token.split("\\.");
        parts[2]=(parts[2].startsWith("A") ? "B" : "A")+parts[2].substring(1);
        assertThrows(JwtException.class,()->tokens.validate(String.join(".",parts)));
    }
    @Test void revokedTokenIsRejected() {
        String token=tokens.issue("reader@example.com");
        String id=tokens.validate(token).getId();
        when(jdbc.queryForObject(anyString(),eq(Boolean.class),eq(id))).thenReturn(true);
        assertThrows(JwtException.class,()->tokens.validate(token));
    }
    @Test void logoutRevokesBothBearerAndCookieTokens() {
        String headerToken=tokens.issue("reader@example.com");
        String cookieToken=tokens.issue("reader@example.com");
        String headerId=tokens.validate(headerToken).getId();
        String cookieId=tokens.validate(cookieToken).getId();
        var request=new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("Authorization","Bearer "+headerToken);
        request.setCookies(new jakarta.servlet.http.Cookie(JwtService.COOKIE,cookieToken));
        tokens.logout(request,new org.springframework.mock.web.MockHttpServletResponse());
        verify(jdbc).update(anyString(),eq(headerId),any(java.sql.Timestamp.class));
        verify(jdbc).update(anyString(),eq(cookieId),any(java.sql.Timestamp.class));
    }
    @Test void secureFlagIsPassedThroughToCookieSettings() {
        assertFalse(new JwtService(java.util.Base64.getEncoder().encodeToString(new byte[32]),false,jdbc).isSecureCookie());
        assertTrue(new JwtService(java.util.Base64.getEncoder().encodeToString(new byte[32]),true,jdbc).isSecureCookie());
    }
}

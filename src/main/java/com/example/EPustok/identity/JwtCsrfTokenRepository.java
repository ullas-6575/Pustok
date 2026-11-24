package com.example.EPustok.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Authenticated forms use a token bound to the existing HttpOnly JWT cookie. */
public final class JwtCsrfTokenRepository implements CsrfTokenRepository {
    private final CookieCsrfTokenRepository anonymous = new CookieCsrfTokenRepository();
    public JwtCsrfTokenRepository(boolean secureCookie) {
        anonymous.setCookieName("PUSTOK_CSRF");
        anonymous.setCookiePath("/");
        anonymous.setCookieCustomizer(cookie -> cookie.secure(secureCookie).sameSite("Lax").path("/"));
    }
    private String jwt(HttpServletRequest request) {
        if (request.getCookies() != null) for (var cookie : request.getCookies())
            if (JwtService.COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank()) return cookie.getValue();
        return null;
    }
    private CsrfToken bound(String jwt) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(("pustok-form-csrf:" + jwt).getBytes(StandardCharsets.UTF_8));
            return new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", HexFormat.of().formatHex(digest));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @Override public CsrfToken generateToken(HttpServletRequest request) {
        String jwt = jwt(request); return jwt == null ? anonymous.generateToken(request) : bound(jwt);
    }
    @Override public CsrfToken loadToken(HttpServletRequest request) {
        String jwt = jwt(request); return jwt == null ? anonymous.loadToken(request) : bound(jwt);
    }
    @Override public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        if (jwt(request) == null) anonymous.saveToken(token, request, response);
    }
}

package com.example.EPustok.identity;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtFilter extends OncePerRequestFilter {
    private final JwtService tokens;
    private final UserDetailsService users;
    public JwtFilter(JwtService tokens, UserDetailsService users) { this.tokens=tokens; this.users=users; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String token = tokens.extract(request);
        if (token != null) {
            try {
                var jwt = tokens.validate(token);
                var user = users.loadUserByUsername(jwt.getSubject());
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
                SecurityContextHolder.setContext(context);
            } catch (org.springframework.security.oauth2.jwt.JwtException | org.springframework.security.core.AuthenticationException ex) {
                SecurityContextHolder.clearContext();
                tokens.cookie(response, "");
            }
        }
        chain.doFilter(request,response);
    }
}

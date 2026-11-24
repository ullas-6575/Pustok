package com.example.EPustok.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;

@Configuration
@org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private static final String CSRF_MESSAGE = "The security token is missing or expired. Load a fresh page and try again.";
    private static final String FORBIDDEN_MESSAGE = "Your current account does not have access to this action.";
    private static final String AUTHENTICATION_MESSAGE = "Authentication is required.";

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmailIgnoreCase(email)
                .map(user -> org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                        .password(user.getPasswordHash()).roles(user.getRole()).disabled(!user.isEnabled()).build())
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found"));
    }
    @Bean
    JwtCsrfTokenRepository csrfTokens(JwtService tokens) { return new JwtCsrfTokenRepository(tokens.isSecureCookie()); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception { return config.getAuthenticationManager(); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService tokens, UserDetailsService users, JwtCsrfTokenRepository csrfTokens) throws Exception {
        return http.sessionManagement(session -> session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens)
                    .ignoringRequestMatchers(request -> isApi(request) && request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")))
                .addFilterBefore(new JwtFilter(tokens, users), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.accessDeniedHandler((request, response, denied) -> {
                    boolean csrfFailure = denied instanceof CsrfException;
                    if (csrfFailure && log.isDebugEnabled()) {
                        boolean cookiePresent = request.getCookies() != null && java.util.Arrays.stream(request.getCookies()).anyMatch(c -> "PUSTOK_CSRF".equals(c.getName()));
                        String[] submitted = request.getParameterValues("_csrf");
                        log.debug("CSRF rejected for {}: cookiePresent={}, formTokenCount={}, failure={}",
                                request.getRequestURI(), cookiePresent, submitted == null ? 0 : submitted.length, denied.getClass().getSimpleName());
                    }
                    if (isApi(request)) {
                        writeError(response, 403, csrfFailure ? CSRF_MESSAGE : FORBIDDEN_MESSAGE);
                        return;
                    }
                    response.setStatus(403);
                    response.setContentType("text/html;charset=UTF-8");
                    response.getWriter().write("<!doctype html><html><head><title>Request could not be completed</title></head><body><h1>Request could not be completed</h1><p>" +
                        (csrfFailure ? "The form security token is missing or expired. Open a fresh page and submit again." : "Your current account does not have access to this action. Seller applications require a BUYER account.") +
                        "</p><p><a href=\"/\">Open home</a> | <a href=\"/signin\">Sign in again</a></p></body></html>");
                }).authenticationEntryPoint((request, response, error) -> {
                    if (isApi(request)) writeError(response, 401, AUTHENTICATION_MESSAGE); else response.sendRedirect("/signin");
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/signup", "/signin", "/error", "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/api/auth/signup", "/api/auth/signin", "/api/auth/register", "/api/auth/login", "/api/auth/csrf").permitAll()
                        .requestMatchers("/admin", "/admin/**", "/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/seller", "/seller/**").hasRole("SELLER")
                        .requestMatchers("/buyer/seller-application", "/buyer/seller-application/**").hasRole("BUYER")
                        .requestMatchers("/buyer", "/buyer/**").hasAnyRole("BUYER", "SELLER")
                        .anyRequest().authenticated())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .build();
    }

    private static boolean isApi(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/");
    }

    /** JSON error body for API paths. Messages are fixed constants, never request data. */
    private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
}

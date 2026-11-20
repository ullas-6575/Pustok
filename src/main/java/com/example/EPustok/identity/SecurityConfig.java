package com.example.EPustok.identity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {
    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmailIgnoreCase(email)
                .map(user -> org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                        .password(user.getPasswordHash()).roles(user.getRole()).build())
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found"));
    }
    @Bean
    JwtCsrfTokenRepository csrfTokens() { return new JwtCsrfTokenRepository(); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception { return config.getAuthenticationManager(); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService tokens, UserDetailsService users, JwtCsrfTokenRepository csrfTokens) throws Exception {
        return http.sessionManagement(session -> session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens)
                    .ignoringRequestMatchers(request -> request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/") && request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")))
                .addFilterBefore(new JwtFilter(tokens, users), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.accessDeniedHandler((request, response, denied) -> {
                    boolean csrfFailure = denied instanceof org.springframework.security.web.csrf.CsrfException;
                    String csrfDetail = "";
                    if (csrfFailure) {
                        boolean cookiePresent = request.getCookies() != null && java.util.Arrays.stream(request.getCookies()).anyMatch(c -> "PUSTOK_CSRF".equals(c.getName()));
                        String[] submitted = request.getParameterValues("_csrf");
                        csrfDetail = " Cookie present: " + cookiePresent + "; form token count: " + (submitted == null ? 0 : submitted.length) + "; failure: " + denied.getClass().getSimpleName() + ".";
                    }
                    response.setStatus(403);
                    response.setContentType("text/html;charset=UTF-8");
                    response.getWriter().write("<!doctype html><html><head><title>Request could not be completed</title></head><body><h1>Request could not be completed</h1><p>" +
                        (csrfFailure ? "The form security token is missing or expired. Open a fresh page and submit again." : "Your current account does not have access to this action. Seller applications require a BUYER account.") +
                        csrfDetail + "</p><p><a href=\"/\">Open home</a> | <a href=\"/signin\">Sign in again</a></p></body></html>");
                }).authenticationEntryPoint((request, response, error) -> {
                    if (request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/")) response.sendError(401); else response.sendRedirect("/signin");
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/signup", "/signin", "/error", "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/api/auth/signup", "/api/auth/signin", "/api/auth/register", "/api/auth/login", "/api/auth/csrf").permitAll()
                        .requestMatchers("/admin", "/admin/**", "/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/seller", "/seller/**", "/api/seller/**").hasRole("SELLER")
                        .requestMatchers("/buyer/seller-application", "/buyer/seller-application/**").hasRole("BUYER")
                        .requestMatchers("/buyer", "/buyer/**", "/api/buyer/**").hasAnyRole("BUYER", "SELLER")
                        .anyRequest().authenticated())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .build();
    }
}

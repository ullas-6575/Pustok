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
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception { return config.getAuthenticationManager(); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService tokens, UserDetailsService users) throws Exception {
        return http.sessionManagement(session -> session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.csrfTokenRepository(new org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository())
                    .ignoringRequestMatchers(request -> request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/") && request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")))
                .addFilterBefore(new JwtFilter(tokens, users), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, error) -> {
                    if (request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/")) response.sendError(401); else response.sendRedirect("/signin");
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/signup", "/signin", "/error", "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/api/auth/signup", "/api/auth/signin", "/api/auth/register", "/api/auth/login", "/api/auth/csrf").permitAll()
                        .requestMatchers("/admin", "/admin/**", "/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/seller", "/seller/**", "/api/seller/**").hasRole("SELLER")
                        .requestMatchers("/buyer", "/buyer/**", "/api/buyer/**").hasRole("BUYER")
                        .anyRequest().authenticated())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .build();
    }
}

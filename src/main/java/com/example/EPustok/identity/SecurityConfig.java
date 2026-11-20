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
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/signup", "/signin", "/error", "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/api/auth/signup", "/api/auth/signin").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.logoutUrl("/signout").logoutSuccessUrl("/signin?logout"))
                .build();
    }
}

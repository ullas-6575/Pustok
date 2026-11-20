package com.example.EPustok.identity;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit one-time creation; never promotes an existing public account. */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final boolean enabled;
    private final String email;
    private final String password;
    public AdminBootstrap(UserRepository users, PasswordEncoder passwords,
            @Value("${ADMIN_BOOTSTRAP_ENABLED:false}") boolean enabled,
            @Value("${ADMIN_EMAIL:}") String email, @Value("${ADMIN_PASSWORD:}") String password) {
        this.users=users; this.passwords=passwords; this.enabled=enabled; this.email=email; this.password=password;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || normalized.length()>254)
            throw new IllegalStateException("Set a valid ADMIN_EMAIL for bootstrap");
        var existing = users.findByEmailIgnoreCase(normalized);
        if (existing.isPresent()) {
            if (!existing.get().getRole().equals("ADMIN"))
                throw new IllegalStateException("Admin bootstrap refuses to promote an existing non-admin; use a new email");
            return; // Do not reset passwords on restart.
        }
        int bytes=password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (password.isBlank() || password.length()<12 || bytes>72)
            throw new IllegalStateException("ADMIN_PASSWORD must have at least 12 characters and at most 72 UTF-8 bytes");
        User admin = new User("Administrator", normalized, passwords.encode(password));
        admin.assignRole(Role.ADMIN);
        users.saveAndFlush(admin);
    }
}

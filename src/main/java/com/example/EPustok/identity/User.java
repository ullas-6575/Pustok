package com.example.EPustok.identity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "identity_users", uniqueConstraints = @UniqueConstraint(name = "uk_identity_users_email", columnNames = "email"))
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 254)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Column(nullable = false, length = 20)
    private String role = "BUYER";
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    protected User() {}
    public User(String name, String email, String passwordHash) {
        this.name = name; this.email = email; this.passwordHash = passwordHash;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public void assignRole(Role role) { this.role = role.name(); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}

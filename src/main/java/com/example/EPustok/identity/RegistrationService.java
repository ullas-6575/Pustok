package com.example.EPustok.identity;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public registration rules shared by browser and JSON signup. Always creates BUYER accounts. */
@Service
public class RegistrationService {
    /** BCrypt silently truncates input after 72 UTF-8 bytes, so longer passwords are rejected instead. */
    static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwords;

    public RegistrationService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Transactional
    public User register(String name, String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        requireBcryptSafePassword(password);
        if (users.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DuplicateEmailException();
        }
        try {
            return users.saveAndFlush(new User(name.trim(), normalizedEmail, passwords.encode(password)));
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request won the unique email constraint after our pre-check.
            throw new DuplicateEmailException();
        }
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static void requireBcryptSafePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new InvalidPasswordException("Password must be at most 72 UTF-8 bytes");
        }
    }

    public static class DuplicateEmailException extends RuntimeException {
        public DuplicateEmailException() { super("This email is already registered"); }
    }

    public static class InvalidPasswordException extends RuntimeException {
        public InvalidPasswordException(String message) { super(message); }
    }
}

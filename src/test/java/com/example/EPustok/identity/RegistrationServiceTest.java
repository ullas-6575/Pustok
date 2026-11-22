package com.example.EPustok.identity;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegistrationServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final RegistrationService service = new RegistrationService(users, encoder);

    @Test void registersNormalizedBuyerWithHashedPassword() {
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        User saved = service.register("  Reader  ", "  Reader@Example.COM ", "password123");
        assertEquals("Reader", saved.getName());
        assertEquals("reader@example.com", saved.getEmail());
        assertEquals("BUYER", saved.getRole());
        assertTrue(encoder.matches("password123", saved.getPasswordHash()));
    }

    @Test void duplicateEmailIsRejectedBeforeInsert() {
        when(users.existsByEmailIgnoreCase("reader@example.com")).thenReturn(true);
        assertThrows(RegistrationService.DuplicateEmailException.class,
                () -> service.register("Reader", "reader@example.com", "password123"));
        verify(users, never()).saveAndFlush(any(User.class));
    }

    @Test void concurrentUniqueConstraintViolationBecomesDuplicateEmail() {
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("uk_identity_users_email"));
        assertThrows(RegistrationService.DuplicateEmailException.class,
                () -> service.register("Reader", "reader@example.com", "password123"));
    }

    @Test void passwordLimitIsCheckedInUtf8BytesNotCharacters() {
        String exactlyLimit = "a".repeat(RegistrationService.BCRYPT_MAX_BYTES);
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertDoesNotThrow(() -> service.register("Reader", "reader@example.com", exactlyLimit));

        String overLimit = "é".repeat(37); // 37 characters, 74 UTF-8 bytes
        assertThrows(RegistrationService.InvalidPasswordException.class,
                () -> service.register("Reader", "other@example.com", overLimit));
        verify(users, times(1)).saveAndFlush(any(User.class));
        verify(users, never()).existsByEmailIgnoreCase("other@example.com");
    }
}

package com.example.EPustok.identity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
class AdminBootstrapTest {
 @Test void disabledCreatesNothing() {
  var users=mock(UserRepository.class);
  new AdminBootstrap(users,new BCryptPasswordEncoder(),false,"","").run(null);
  verifyNoInteractions(users);
 }
 @Test void createsAdminWithHashedPassword() {
  var users=mock(UserRepository.class); var encoder=new BCryptPasswordEncoder();
  when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.empty());
  new AdminBootstrap(users,encoder,true,"admin@example.com","strong-pass-123").run(null);
  var capture=org.mockito.ArgumentCaptor.forClass(User.class);verify(users).saveAndFlush(capture.capture());
  assertEquals("ADMIN",capture.getValue().getRole());assertTrue(encoder.matches("strong-pass-123",capture.getValue().getPasswordHash()));
 }
 @Test void refusesToPromoteExistingBuyer() {
  var users=mock(UserRepository.class);
  when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(new User("Buyer","admin@example.com","hash")));
  assertThrows(IllegalStateException.class,()->new AdminBootstrap(users,new BCryptPasswordEncoder(),true,"admin@example.com","strong-pass-123").run(null));
  verify(users,never()).saveAndFlush(any());
 }
 @Test void existingAdminPasswordIsNotReset() {
  var users=mock(UserRepository.class);var user=new User("Admin","admin@example.com","hash");user.assignRole(Role.ADMIN);
  when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(user));
  new AdminBootstrap(users,new BCryptPasswordEncoder(),true,"admin@example.com","").run(null);
  verify(users,never()).saveAndFlush(any());assertEquals("hash",user.getPasswordHash());
 }
}

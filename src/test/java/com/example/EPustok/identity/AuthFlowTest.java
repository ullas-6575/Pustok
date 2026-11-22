package com.example.EPustok.identity;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Browser and JSON authentication behavior through the real SecurityFilterChain. */
@SpringJUnitConfig(AuthFlowTest.Config.class)
@WebAppConfiguration
class AuthFlowTest {
    @Configuration @EnableWebMvc @Import(SecurityConfig.class)
    static class Config {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean JdbcTemplate jdbc() { return mock(JdbcTemplate.class); }
        @Bean JwtService tokens(JdbcTemplate jdbc) { return new JwtService(Base64.getEncoder().encodeToString(new byte[32]), false, jdbc); }
        @Bean RegistrationService registration(UserRepository users, PasswordEncoder encoder) { return new RegistrationService(users, encoder); }
        @Bean AuthController authController(RegistrationService registration, AuthenticationManager manager, JwtService tokens) { return new AuthController(registration, manager, tokens); }
        @Bean AuthApiController authApi(AuthenticationManager manager, JwtService tokens, RegistrationService registration) { return new AuthApiController(manager, tokens, registration); }
        @Bean Probe probe() { return new Probe(); }
    }
    @RestController static class Probe {
        @GetMapping("/seller/probe") String seller() { return "seller"; }
    }

    private static final String PASSWORD = "correct-password";
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService tokens;
    @Autowired PasswordEncoder passwords;
    MockMvc mvc;

    @BeforeEach void setup() {
        reset(users, jdbc);
        User buyer = new User("Reader", "reader@example.com", passwords.encode(PASSWORD));
        when(users.findByEmailIgnoreCase("reader@example.com")).thenReturn(Optional.of(buyer));
        when(users.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void anonymousBrowserRequestRedirectsToSignin() throws Exception {
        mvc.perform(get("/seller/probe")).andExpect(status().isFound()).andExpect(redirectedUrl("/signin"));
    }

    @Test void anonymousRootRedirectsToSignin() throws Exception {
        mvc.perform(get("/")).andExpect(status().isFound()).andExpect(redirectedUrl("/signin"));
    }

    @Test void failedBrowserLoginShowsErrorWithoutIssuingCookie() throws Exception {
        var result = mvc.perform(post("/signin").with(csrf())
                        .param("email", "reader@example.com").param("password", "wrong-password"))
                .andExpect(status().isOk()).andExpect(view().name("auth/signin")).andReturn();
        assertTrue(result.getResponse().getHeaders("Set-Cookie").isEmpty());
    }

    @Test void failedApiLoginReturnsUnauthorizedOnAliasRoute() throws Exception {
        mvc.perform(post("/api/auth/signin").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"reader@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void successfulLoginRotatesTokenAndInvalidatesPreLoginSession() throws Exception {
        String previous = tokens.issue("reader@example.com");
        String previousId = tokens.validate(previous).getId();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("stale", "value");

        var result = mvc.perform(post("/signin").with(csrf()).session(session)
                        .cookie(new Cookie(JwtService.COOKIE, previous))
                        .param("email", "reader@example.com").param("password", PASSWORD))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/catalog")).andReturn();

        assertTrue(session.isInvalid(), "pre-login HTTP session must be invalidated");
        verify(jdbc).update(anyString(), eq(previousId), any(java.sql.Timestamp.class));
        String setCookie = String.join(";", result.getResponse().getHeaders("Set-Cookie"));
        assertTrue(setCookie.contains(JwtService.COOKIE + "="), "login must set a new token cookie");
        assertFalse(setCookie.contains(previous), "login must not reuse the presented token");
    }

    @Test void duplicateEmailRaceFromConcurrentSignupShowsFieldError() throws Exception {
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));
        mvc.perform(post("/signup").with(csrf())
                        .param("name", "Reader").param("email", "new@example.com").param("password", "password123"))
                .andExpect(status().isOk()).andExpect(view().name("auth/signup"))
                .andExpect(model().attributeHasFieldErrors("form", "email"));
    }

    @Test void apiDuplicateEmailReturnsConflict() throws Exception {
        when(users.existsByEmailIgnoreCase("reader@example.com")).thenReturn(true);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                        .content("{\"name\":\"Reader\",\"email\":\"reader@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict());
    }

    @Test void passwordOverBcryptByteLimitIsRejectedBeforeStorage() throws Exception {
        String multiByte = "ক".repeat(25); // 25 characters, 75 UTF-8 bytes
        mvc.perform(post("/signup").with(csrf())
                        .param("name", "Reader").param("email", "new@example.com").param("password", multiByte))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "password"));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                        .content("{\"name\":\"Reader\",\"email\":\"new@example.com\",\"password\":\"" + multiByte + "\"}"))
                .andExpect(status().isBadRequest());
        verify(users, never()).saveAndFlush(any(User.class));
    }

    @Test void browserLogoutRevokesTokenAndClearsCookie() throws Exception {
        String token = tokens.issue("reader@example.com");
        String id = tokens.validate(token).getId();
        var result = mvc.perform(post("/signout").with(csrf()).cookie(new Cookie(JwtService.COOKIE, token)))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/signin?logout")).andReturn();
        verify(jdbc).update(anyString(), eq(id), any(java.sql.Timestamp.class));
        assertTrue(String.join(";", result.getResponse().getHeaders("Set-Cookie")).contains("Max-Age=0"));
    }

    /** Every /api/auth path allowed in SecurityConfig must reach a handler; an empty body fails validation (400), not routing (404). */
    @Test void permittedApiAuthPathsAllHaveHandlers() throws Exception {
        for (String path : List.of("/api/auth/signup", "/api/auth/signin", "/api/auth/register", "/api/auth/login")) {
            mvc.perform(post(path).with(csrf()).contentType("application/json").content("{}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk());
    }

    @Test void forgedBearerLogoutIsRejectedBeforeAnyRevocation() throws Exception {
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(jdbc);
    }
}

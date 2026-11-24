package com.example.EPustok.identity;

import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.Cookie;

@SpringJUnitConfig(JwtAccessTest.Config.class)
@WebAppConfiguration
class JwtAccessTest {
    @Configuration @EnableWebMvc @Import(SecurityConfig.class)
    static class Config {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean JdbcTemplate jdbc() { return mock(JdbcTemplate.class); }
        @Bean JwtService tokens(JdbcTemplate jdbc) { return new JwtService(Base64.getEncoder().encodeToString(new byte[32]),false,jdbc); }
        @Bean RegistrationService registration(UserRepository users,PasswordEncoder encoder) { return new RegistrationService(users,encoder); }
        @Bean AuthRateLimiter authRateLimiter() { return new AuthRateLimiter(); }
        @Bean AuthApiController auth(AuthenticationManager manager,JwtService jwt,RegistrationService registration,AuthRateLimiter limiter) { return new AuthApiController(manager,jwt,registration,limiter); }
        @Bean AdminService adminService(UserRepository users,JdbcTemplate jdbc) { return new AdminService(users,jdbc); }
        @Bean AdminApiController adminApi(AdminService admin) { return new AdminApiController(admin); }
        @Bean Probe probe() { return new Probe(); }
    }
    @RestController static class Probe {
        @GetMapping("/seller/probe") String seller() { return "seller"; }
        @GetMapping("/buyer/probe") String buyer() { return "buyer"; }
    }
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService tokens;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthRateLimiter limiter;
    MockMvc mvc;
    @BeforeEach void setup() {
        reset(users,jdbc);
        limiter.reset();
        for(Role role:Role.values()) {
            User user=new User(role.name(),role.name().toLowerCase()+"@example.com",passwords.encode("correct-password"));
            user.assignRole(role);
            when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        }
        when(users.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(org.springframework.data.domain.Page.empty());
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @Test void bearerRoleBoundariesAndAnonymousRejection() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users").header("Authorization","Bearer "+tokens.issue("buyer@example.com"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization","Bearer "+tokens.issue("admin@example.com"))).andExpect(status().isOk());
        mvc.perform(get("/seller/probe").header("Authorization","Bearer "+tokens.issue("seller@example.com"))).andExpect(status().isOk());
        mvc.perform(get("/seller/probe").header("Authorization","Bearer "+tokens.issue("buyer@example.com"))).andExpect(status().isForbidden());
        mvc.perform(get("/buyer/probe").header("Authorization","Bearer "+tokens.issue("buyer@example.com"))).andExpect(status().isOk());
    }
    @org.springframework.test.annotation.DirtiesContext(methodMode=org.springframework.test.annotation.DirtiesContext.MethodMode.BEFORE_METHOD)
    @Test void browserCsrfCookieSurvivesWithoutHttpSession() throws Exception {
        var page=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var cookie=page.getResponse().getCookie("PUSTOK_CSRF");
        assertNotNull(cookie);
        var authCookie = new Cookie(JwtService.COOKIE,tokens.issue("buyer@example.com"));
        var authenticated = mvc.perform(get("/api/auth/csrf").cookie(authCookie)).andExpect(status().isOk()).andReturn();
        String token = ((org.springframework.security.web.csrf.CsrfToken)authenticated.getRequest().getAttribute("_csrf")).getToken();
        mvc.perform(post("/api/auth/logout").cookie(authCookie).param("_csrf",token)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").cookie(authCookie).param("_csrf","wrong-token")).andExpect(status().isForbidden());

    }
    @Test void sellerCannotReapplyForSellerAccess() throws Exception {
        mvc.perform(get("/buyer/seller-application").header("Authorization","Bearer "+tokens.issue("seller@example.com"))).andExpect(status().isForbidden());
    }
    @Test void cookieAuthRequiresCsrfAndLogoutRevokesToken() throws Exception {
        String token=tokens.issue("buyer@example.com");
        String id=tokens.validate(token).getId();
        mvc.perform(get("/api/auth/me").cookie(new Cookie(JwtService.COOKIE,token))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").cookie(new Cookie(JwtService.COOKIE,token))).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").cookie(new Cookie(JwtService.COOKIE,token)).with(csrf())).andExpect(status().isOk());
        verify(jdbc).update(anyString(),eq(id),any(java.sql.Timestamp.class));
        when(jdbc.queryForObject(anyString(),eq(Boolean.class),eq(id))).thenReturn(true);
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    @Test void loginChecksPasswordAndTokenWorks() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"email\":\"buyer@example.com\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"email\":\"buyer@example.com\",\"password\":\"correct-password\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"));
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
    }
    @Test void signupCannotRequestAdminRole() throws Exception {
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation->{
            User user=invocation.getArgument(0);
            assertEquals("BUYER",user.getRole());
            var field=User.class.getDeclaredField("id"); field.setAccessible(true);field.set(user,99L);return user;
        });
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content("{\"name\":\"Reader\",\"email\":\"new@example.com\",\"password\":\"password123\",\"role\":\"ADMIN\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("BUYER"));
    }
    @Test void roleChangeTakesEffectForExistingJwt() throws Exception {
        String token=tokens.issue("buyer@example.com");
        User user=users.findByEmailIgnoreCase("buyer@example.com").orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        user.assignRole(Role.SELLER);
        mvc.perform(get("/seller/probe").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(get("/buyer/probe").header("Authorization","Bearer "+token)).andExpect(status().isOk());
    }
    @Test void disabledAccountLosesExistingTokenAndCannotLogin() throws Exception {
        String token=tokens.issue("buyer@example.com");
        User user=users.findByEmailIgnoreCase("buyer@example.com").orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        user.setEnabled(false);
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"email\":\"buyer@example.com\",\"password\":\"correct-password\"}")).andExpect(status().isUnauthorized());
    }
}

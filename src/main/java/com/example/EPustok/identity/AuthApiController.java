package com.example.EPustok.identity;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {
    private final AuthenticationManager authentication;
    private final JwtService tokens;
    private final RegistrationService registration;
    private final AuthRateLimiter limiter;
    public AuthApiController(AuthenticationManager authentication, JwtService tokens, RegistrationService registration, AuthRateLimiter limiter) {
        this.authentication=authentication; this.tokens=tokens; this.registration=registration; this.limiter=limiter;
    }
    @GetMapping("/csrf")
    public Map<String,String> csrf(CsrfToken csrf) { return Map.of("headerName",csrf.getHeaderName(),"token",csrf.getToken()); }
    @PostMapping({"/login","/signin"})
    public Map<String,Object> login(@Valid @RequestBody AuthController.SigninForm form, HttpServletRequest request) {
        String ip = AuthRateLimiter.clientIp(request);
        String email = AuthRateLimiter.normalizeEmail(form.getEmail());
        if (limiter.isLoginBlocked(ip, email)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, AuthController.LOGIN_THROTTLED);
        try {
            var result = authentication.authenticate(new UsernamePasswordAuthenticationToken(email, form.getPassword()));
            limiter.recordLoginSuccess(email);
            return Map.of("accessToken",tokens.rotate(request,result.getName()),"tokenType","Bearer","expiresIn",3600);
        } catch (org.springframework.security.core.AuthenticationException ex) {
            limiter.recordLoginFailure(ip, email);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password");
        }
    }
    @PostMapping({"/register","/signup"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> register(@Valid @RequestBody AuthController.SignupForm form, HttpServletRequest request) {
        String ip = AuthRateLimiter.clientIp(request);
        if (limiter.isSignupBlocked(ip)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, AuthController.SIGNUP_THROTTLED);
        try {
            User user = registration.register(form.getName(), form.getEmail(), form.getPassword());
            return Map.of("id",user.getId(),"email",user.getEmail(),"role",user.getRole());
        } catch (RegistrationService.DuplicateEmailException ex) {
            limiter.recordSignupFailure(ip);
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Email already registered");
        } catch (RegistrationService.InvalidPasswordException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,ex.getMessage());
        }
    }
    @GetMapping("/me")
    public Map<String,Object> me(Authentication auth) { return Map.of("email",auth.getName(),"authorities",auth.getAuthorities()); }
    @PostMapping("/logout")
    public void logout(HttpServletRequest request,HttpServletResponse response) { tokens.logout(request,response); }
}

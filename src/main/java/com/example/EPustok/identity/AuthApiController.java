package com.example.EPustok.identity;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Locale;
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
    public AuthApiController(AuthenticationManager authentication, JwtService tokens, RegistrationService registration) {
        this.authentication=authentication; this.tokens=tokens; this.registration=registration;
    }
    @GetMapping("/csrf")
    public Map<String,String> csrf(CsrfToken csrf) { return Map.of("headerName",csrf.getHeaderName(),"token",csrf.getToken()); }
    @PostMapping({"/login","/signin"})
    public Map<String,Object> login(@Valid @RequestBody AuthController.SigninForm form, HttpServletRequest request) {
        try {
            var result = authentication.authenticate(new UsernamePasswordAuthenticationToken(form.getEmail().trim().toLowerCase(Locale.ROOT),form.getPassword()));
            return Map.of("accessToken",tokens.rotate(request,result.getName()),"tokenType","Bearer","expiresIn",3600);
        } catch (org.springframework.security.core.AuthenticationException ex) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password"); }
    }
    @PostMapping({"/register","/signup"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> register(@Valid @RequestBody AuthController.SignupForm form) {
        try {
            User user = registration.register(form.getName(), form.getEmail(), form.getPassword());
            return Map.of("id",user.getId(),"email",user.getEmail(),"role",user.getRole());
        } catch (RegistrationService.DuplicateEmailException ex) {
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

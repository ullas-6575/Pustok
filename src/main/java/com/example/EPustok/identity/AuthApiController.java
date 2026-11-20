package com.example.EPustok.identity;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {
    private final AuthenticationManager authentication;
    private final JwtService tokens;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    public AuthApiController(AuthenticationManager authentication, JwtService tokens, UserRepository users, PasswordEncoder passwords) {
        this.authentication=authentication; this.tokens=tokens; this.users=users; this.passwords=passwords;
    }
    @GetMapping("/csrf")
    public Map<String,String> csrf(CsrfToken csrf) { return Map.of("headerName",csrf.getHeaderName(),"token",csrf.getToken()); }
    @PostMapping({"/login","/signin"})
    public Map<String,Object> login(@Valid @RequestBody AuthController.SigninForm form) {
        try {
            var result = authentication.authenticate(new UsernamePasswordAuthenticationToken(form.getEmail().trim().toLowerCase(Locale.ROOT),form.getPassword()));
            return Map.of("accessToken",tokens.issue(result.getName()),"tokenType","Bearer","expiresIn",3600);
        } catch (org.springframework.security.core.AuthenticationException ex) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password"); }
    }
    @PostMapping({"/register","/signup"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> register(@Valid @RequestBody AuthController.SignupForm form) {
        String email=form.getEmail().trim().toLowerCase(Locale.ROOT);
        if(users.existsByEmailIgnoreCase(email)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Email already registered");
        try {
            User user=users.saveAndFlush(new User(form.getName().trim(),email,passwords.encode(form.getPassword())));
            return Map.of("id",user.getId(),"email",user.getEmail(),"role",user.getRole());
        } catch(org.springframework.dao.DataIntegrityViolationException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT,"Account could not be created"); }
    }
    @GetMapping("/me")
    public Map<String,Object> me(Authentication auth) { return Map.of("email",auth.getName(),"authorities",auth.getAuthorities()); }
    @PostMapping("/logout")
    public void logout(HttpServletRequest request,HttpServletResponse response) { tokens.revoke(tokens.extract(request)); tokens.cookie(response,""); }
}

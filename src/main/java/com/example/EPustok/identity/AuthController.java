package com.example.EPustok.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager) {
        this.users = users; this.passwordEncoder = passwordEncoder; this.authenticationManager = authenticationManager;
    }

    @GetMapping("/signup")
    public String signupPage(Model model) { model.addAttribute("form", new SignupForm()); return "auth/signup"; }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute("form") SignupForm form, BindingResult errors, RedirectAttributes redirect) {
        if (errors.hasErrors()) return "auth/signup";
        String email = form.getEmail().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            errors.rejectValue("email", "duplicate", "This email is already registered");
            return "auth/signup";
        }
        users.save(new User(form.getName().trim(), email, passwordEncoder.encode(form.getPassword())));
        redirect.addFlashAttribute("message", "Account created. Please sign in.");
        return "redirect:/signin";
    }

    @GetMapping("/signin")
    public String signinPage(Model model) { model.addAttribute("form", new SigninForm()); return "auth/signin"; }

    @PostMapping("/signin")
    public String signin(@Valid @ModelAttribute("form") SigninForm form, BindingResult errors, HttpServletRequest request, HttpServletResponse response) {
        if (errors.hasErrors()) return "auth/signin";
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(form.getEmail().trim().toLowerCase(), form.getPassword()));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, request, response);
            return "redirect:/";
        } catch (org.springframework.security.core.AuthenticationException ex) {
            errors.reject("invalid", "Email or password is incorrect");
            return "auth/signin";
        }
    }

    @PostMapping("/signout")
    public String signout(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        return "redirect:/signin";
    }

    public static class SignupForm {
        @NotBlank @Size(max=120) private String name;
        @NotBlank @Email @Size(max=254) private String email;
        @NotBlank @Size(min=8,max=72) private String password;
        public String getName(){return name;} public void setName(String v){name=v;}
        public String getEmail(){return email;} public void setEmail(String v){email=v;}
        public String getPassword(){return password;} public void setPassword(String v){password=v;}
    }
    public static class SigninForm {
        @NotBlank @Email private String email;
        @NotBlank private String password;
        public String getEmail(){return email;} public void setEmail(String v){email=v;}
        public String getPassword(){return password;} public void setPassword(String v){password=v;}
    }
}

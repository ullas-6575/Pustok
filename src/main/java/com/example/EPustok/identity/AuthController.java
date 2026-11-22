package com.example.EPustok.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    private final RegistrationService registration;
    private final AuthenticationManager authenticationManager;
    private final JwtService tokens;

    public AuthController(RegistrationService registration, AuthenticationManager authenticationManager, JwtService tokens) {
        this.registration = registration; this.authenticationManager = authenticationManager; this.tokens = tokens;
    }

    @GetMapping("/signup")
    public String signupPage(Model model) { model.addAttribute("form", new SignupForm()); return "auth/signup"; }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute("form") SignupForm form, BindingResult errors, RedirectAttributes redirect) {
        if (errors.hasErrors()) return "auth/signup";
        try {
            registration.register(form.getName(), form.getEmail(), form.getPassword());
        } catch (RegistrationService.DuplicateEmailException ex) {
            errors.rejectValue("email", "duplicate", ex.getMessage());
            return "auth/signup";
        } catch (RegistrationService.InvalidPasswordException ex) {
            errors.rejectValue("password", "bytes", ex.getMessage());
            return "auth/signup";
        }
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
                    new UsernamePasswordAuthenticationToken(form.getEmail().trim().toLowerCase(java.util.Locale.ROOT), form.getPassword()));
            // Renew the pre-login state: drop any HTTP session (flash/CSRF leftovers) and rotate the JWT.
            HttpSession session = request.getSession(false);
            if (session != null) session.invalidate();
            tokens.cookie(response, tokens.rotate(request, authentication.getName()));
            return "redirect:/catalog";
        } catch (org.springframework.security.core.AuthenticationException ex) {
            errors.reject("invalid", "Email or password is incorrect");
            return "auth/signin";
        }
    }

    @PostMapping("/signout")
    public String signout(HttpServletRequest request, HttpServletResponse response) {
        tokens.logout(request, response);
        return "redirect:/signin?logout";
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

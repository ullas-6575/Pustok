package com.example.EPustok;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        String email = authentication.getName();
        model.addAttribute("email", email);
        model.addAttribute("displayName", email.contains("@") ? email.substring(0, email.indexOf('@')) : email);
        return "home";
    }
}

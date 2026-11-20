package com.example.EPustok;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    private final com.example.EPustok.market.Books books;
    public HomeController(com.example.EPustok.market.Books books){this.books=books;}
    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        model.addAttribute("books",books.findAll());
        String email = authentication.getName();
        model.addAttribute("email", email);
        model.addAttribute("displayName", email.contains("@") ? email.substring(0, email.indexOf('@')) : email);
        return "home";
    }
}

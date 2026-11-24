package com.example.EPustok;

import com.example.EPustok.market.Books;
import com.example.EPustok.market.Categories;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    private final Books books;
    private final Categories categories;

    public HomeController(Books books, Categories categories) {
        this.books = books;
        this.categories = categories;
    }

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        model.addAttribute("books", books.findTop12ByOrderByIdDesc());
        model.addAttribute("categories", categories.findAll());
        String email = authentication.getName();
        model.addAttribute("email", email);
        model.addAttribute("displayName", email.contains("@") ? email.substring(0, email.indexOf('@')) : email);
        return "home";
    }
}

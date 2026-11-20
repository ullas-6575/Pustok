package com.example.EPustok.market;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Serves the shared product catalog page after login so every user sees
 * the same list of books published by approved sellers.
 */
@Controller
public class CatalogController {
    private final Books books;
    private final Categories categories;
    private final SellerProfiles profiles;

    public CatalogController(Books books, Categories categories, SellerProfiles profiles) {
        this.books = books;
        this.categories = categories;
        this.profiles = profiles;
    }

    @GetMapping("/catalog")
    public String catalog(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String q,
            Authentication authentication,
            Model model) {
        String normalizedQuery = q == null ? "" : q.trim().toLowerCase();
        List<Book> filteredBooks = books.findAll().stream()
                .filter(book -> categoryId == null || (book.getCategory() != null && categoryId.equals(book.getCategory().getId())))
                .filter(book -> normalizedQuery.isEmpty()
                        || book.getTitle().toLowerCase().contains(normalizedQuery)
                        || book.getAuthor().toLowerCase().contains(normalizedQuery))
                .toList();

        Map<Long, String> sellerNames = profiles.findAll().stream()
                .collect(Collectors.toMap(SellerProfile::getId, SellerProfile::getShopName, (first, second) -> first));

        String email = authentication.getName();
        String displayName = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;

        model.addAttribute("books", filteredBooks);
        model.addAttribute("categories", categories.findAll());
        model.addAttribute("sellerNames", sellerNames);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("query", q == null ? "" : q.trim());
        model.addAttribute("email", email);
        model.addAttribute("displayName", displayName);
        return "catalog";
    }
}

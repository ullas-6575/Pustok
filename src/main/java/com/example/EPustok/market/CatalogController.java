package com.example.EPustok.market;

import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
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
 * the same seller books as well as community resell and exchange listings.
 */
@Controller
public class CatalogController {
    private final Books books;
    private final Categories categories;
    private final SellerProfiles profiles;
    private final ResellListings resellListings;
    private final UserRepository users;

    public CatalogController(
            Books books,
            Categories categories,
            SellerProfiles profiles,
            ResellListings resellListings,
            UserRepository users) {
        this.books = books;
        this.categories = categories;
        this.profiles = profiles;
        this.resellListings = resellListings;
        this.users = users;
    }

    @GetMapping("/catalog")
    public String catalog(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String q,
            Authentication authentication,
            Model model) {
        String normalizedType = type == null || type.isBlank() ? "all" : type.trim().toLowerCase();
        String normalizedQuery = q == null ? "" : q.trim().toLowerCase();

        boolean includeNew = "all".equals(normalizedType) || "new".equals(normalizedType);
        boolean includeResell = categoryId == null && ("all".equals(normalizedType) || "resell".equals(normalizedType));
        boolean includeExchange = categoryId == null && ("all".equals(normalizedType) || "exchange".equals(normalizedType));

        List<Book> filteredBooks = includeNew
                ? books.findAll().stream()
                        .filter(book -> categoryId == null || (book.getCategory() != null && categoryId.equals(book.getCategory().getId())))
                        .filter(book -> normalizedQuery.isEmpty()
                                || book.getTitle().toLowerCase().contains(normalizedQuery)
                                || book.getAuthor().toLowerCase().contains(normalizedQuery))
                        .toList()
                : List.of();

        List<ResellListing> filteredCommunity = (includeResell || includeExchange)
                ? resellListings.findByStatusOrderByCreatedAtDesc("AVAILABLE").stream()
                        .filter(item -> ("RESELL".equals(item.getListingType()) && includeResell)
                                || ("EXCHANGE".equals(item.getListingType()) && includeExchange))
                        .filter(item -> normalizedQuery.isEmpty()
                                || item.getTitle().toLowerCase().contains(normalizedQuery)
                                || item.getAuthor().toLowerCase().contains(normalizedQuery)
                                || (item.getDesiredBook() != null && item.getDesiredBook().toLowerCase().contains(normalizedQuery)))
                        .toList()
                : List.of();

        Map<Long, String> sellerNames = profiles.findAll().stream()
                .collect(Collectors.toMap(SellerProfile::getId, SellerProfile::getShopName, (first, second) -> first));

        Map<Long, String> listerNames = users.findAll().stream()
                .collect(Collectors.toMap(User::getId, User::getName, (first, second) -> first));

        String email = authentication.getName();
        String displayName = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;

        model.addAttribute("books", filteredBooks);
        model.addAttribute("resellListings", filteredCommunity);
        model.addAttribute("categories", categories.findAll());
        model.addAttribute("sellerNames", sellerNames);
        model.addAttribute("listerNames", listerNames);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("selectedType", normalizedType);
        model.addAttribute("query", q == null ? "" : q.trim());
        model.addAttribute("totalCount", filteredBooks.size() + filteredCommunity.size());
        model.addAttribute("email", email);
        model.addAttribute("displayName", displayName);
        return "catalog";
    }
}

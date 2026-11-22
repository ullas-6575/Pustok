package com.example.EPustok.market;

import com.example.EPustok.cart.CartService;
import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves the shared product catalog page after login so every user sees
 * the same seller books as well as community resell and exchange listings.
 * New books are filtered and paginated in the database; community listings are not paginated yet.
 */
@Controller
public class CatalogController {
    static final int PAGE_SIZE = 12;

    private final Books books;
    private final Categories categories;
    private final SellerProfiles profiles;
    private final ResellListings resellListings;
    private final UserRepository users;
    private final CartService cart;

    public CatalogController(
            Books books,
            Categories categories,
            SellerProfiles profiles,
            ResellListings resellListings,
            UserRepository users,
            CartService cart) {
        this.books = books;
        this.categories = categories;
        this.profiles = profiles;
        this.resellListings = resellListings;
        this.users = users;
        this.cart = cart;
    }

    @GetMapping("/catalog")
    public String catalog(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            Authentication authentication,
            Model model) {
        String normalizedType = type == null || type.isBlank() ? "all" : type.trim().toLowerCase(Locale.ROOT);
        String normalizedQuery = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        boolean includeNew = "all".equals(normalizedType) || "new".equals(normalizedType);
        boolean includeResell = categoryId == null && ("all".equals(normalizedType) || "resell".equals(normalizedType));
        boolean includeExchange = categoryId == null && ("all".equals(normalizedType) || "exchange".equals(normalizedType));

        Page<Book> bookPage = includeNew
                ? books.findAll(bookFilter(categoryId, normalizedQuery),
                        PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by("id")))
                : Page.<Book>empty();

        List<ResellListing> filteredCommunity = (includeResell || includeExchange)
                ? resellListings.findByStatusOrderByCreatedAtDesc("AVAILABLE").stream()
                        .filter(item -> ("RESELL".equals(item.getListingType()) && includeResell)
                                || ("EXCHANGE".equals(item.getListingType()) && includeExchange))
                        .filter(item -> normalizedQuery.isEmpty()
                                || item.getTitle().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                                || item.getAuthor().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                                || (item.getDesiredBook() != null && item.getDesiredBook().toLowerCase(Locale.ROOT).contains(normalizedQuery)))
                        .toList()
                : List.of();

        Set<Long> sellerIds = bookPage.getContent().stream().map(Book::getSellerId).collect(Collectors.toSet());
        Map<Long, String> sellerNames = sellerIds.isEmpty() ? Map.of()
                : profiles.findAllById(sellerIds).stream()
                        .collect(Collectors.toMap(SellerProfile::getId, SellerProfile::getShopName, (first, second) -> first));

        Set<Long> listerIds = filteredCommunity.stream().map(ResellListing::getUserId).collect(Collectors.toSet());
        Map<Long, String> listerNames = listerIds.isEmpty() ? Map.of()
                : users.findNamesByIdIn(listerIds).stream()
                        .collect(Collectors.toMap(UserRepository.UserName::getId, UserRepository.UserName::getName,
                                (first, second) -> first));

        String email = authentication.getName();
        String displayName = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        User currentUser = users.findByEmailIgnoreCase(email).orElse(null);
        boolean canUseCart = canUseCart(authentication);
        int cartCount = canUseCart && currentUser != null ? cart.count(email) : 0;

        model.addAttribute("books", bookPage.getContent());
        model.addAttribute("currentPage", bookPage.getNumber());
        model.addAttribute("totalPages", bookPage.getTotalPages());
        model.addAttribute("hasPrevious", bookPage.hasPrevious());
        model.addAttribute("hasNext", bookPage.hasNext());
        model.addAttribute("resellListings", filteredCommunity);
        model.addAttribute("categories", categories.findAll());
        model.addAttribute("sellerNames", sellerNames);
        model.addAttribute("listerNames", listerNames);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("selectedType", normalizedType);
        model.addAttribute("query", q == null ? "" : q.trim());
        model.addAttribute("totalCount", bookPage.getTotalElements() + filteredCommunity.size());
        model.addAttribute("currentUserId", currentUser != null ? currentUser.getId() : null);
        model.addAttribute("cartCount", cartCount);
        model.addAttribute("email", email);
        model.addAttribute("displayName", displayName);
        return "catalog";
    }

    @GetMapping("/catalog/books/{id}")
    public String bookDetail(@PathVariable long id, Authentication authentication, Model model) {
        Book book = books.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));
        String email = authentication.getName();
        User currentUser = users.findByEmailIgnoreCase(email).orElse(null);
        boolean ownBook = currentUser != null && book.getSellerId().equals(currentUser.getId());
        String displayName = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;

        model.addAttribute("book", book);
        model.addAttribute("sellerName", profiles.findById(book.getSellerId())
                .map(SellerProfile::getShopName).orElse("Pustok Seller"));
        model.addAttribute("isOwnBook", ownBook);
        model.addAttribute("canAddToCart", canUseCart(authentication) && !ownBook && book.getStock() > 0);
        model.addAttribute("displayName", displayName);
        return "book";
    }

    static Specification<Book> bookFilter(Long categoryId, String normalizedQuery) {
        Specification<Book> spec = (root, query, cb) -> cb.conjunction();
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }
        if (!normalizedQuery.isEmpty()) {
            String pattern = "%" + escapeLike(normalizedQuery) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), pattern, '\\'),
                    cb.like(cb.lower(root.get("author")), pattern, '\\')));
        }
        return spec;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static boolean canUseCart(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_BUYER".equals(a.getAuthority()) || "ROLE_SELLER".equals(a.getAuthority()));
    }
}

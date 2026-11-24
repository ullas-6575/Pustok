package com.example.EPustok.market;

import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
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
 * Serves the shared product catalog page after login so every user sees the same seller books as well as community
 * resell and exchange listings. Books and community listings are filtered and paginated in the database.
 * In the mixed "All" view, community cards appear on the first page only; the Resell and Exchange filters page through
 * community listings themselves.
 */
@Controller
public class CatalogController {
    static final int PAGE_SIZE = 12;
    static final int MAX_QUERY_LENGTH = 100;

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
            @RequestParam(defaultValue = "0") int page,
            Authentication authentication,
            Model model) {
        String normalizedType = type == null || type.isBlank() ? "all" : type.trim().toLowerCase(Locale.ROOT);
        String displayQuery = clampQuery(q);
        String normalizedQuery = displayQuery.toLowerCase(Locale.ROOT);
        int requestedPage = Math.max(page, 0);

        boolean includeNew = "all".equals(normalizedType) || "new".equals(normalizedType);
        boolean includeResell = categoryId == null && ("all".equals(normalizedType) || "resell".equals(normalizedType));
        boolean includeExchange = categoryId == null && ("all".equals(normalizedType) || "exchange".equals(normalizedType));
        boolean includeCommunity = includeResell || includeExchange;

        List<String> communityTypes = new ArrayList<>();
        if (includeResell) {
            communityTypes.add("RESELL");
        }
        if (includeExchange) {
            communityTypes.add("EXCHANGE");
        }

        Specification<Book> bookSpec = bookFilter(categoryId, normalizedQuery);
        Page<Book> bookPage = includeNew
                ? pageClamped(requestedPage, p -> books.findAll(bookSpec,
                        PageRequest.of(p, PAGE_SIZE, Sort.by("id"))))
                : Page.empty();

        Page<ResellListing> communityPage;
        if (!includeCommunity) {
            communityPage = Page.empty();
        } else if (includeNew) {
            // Mixed view: the first page only is needed for cards, and its total feeds the header count.
            communityPage = findCommunity(communityTypes, normalizedQuery, 0);
        } else {
            communityPage = pageClamped(requestedPage, p -> findCommunity(communityTypes, normalizedQuery, p));
        }

        // Books drive the pager in the mixed view; community listings drive it in the Resell/Exchange views.
        Page<?> navPage = includeNew ? bookPage : communityPage;
        boolean showCommunityCards = includeCommunity && (!includeNew || navPage.getNumber() == 0);
        List<ResellListing> filteredCommunity = showCommunityCards ? communityPage.getContent() : List.of();

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

        model.addAttribute("books", bookPage.getContent());
        model.addAttribute("currentPage", navPage.getNumber());
        model.addAttribute("totalPages", navPage.getTotalPages());
        model.addAttribute("hasPrevious", navPage.hasPrevious());
        model.addAttribute("hasNext", navPage.hasNext());
        model.addAttribute("resellListings", filteredCommunity);
        model.addAttribute("categories", categories.findAll());
        model.addAttribute("sellerNames", sellerNames);
        model.addAttribute("listerNames", listerNames);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("selectedType", normalizedType);
        model.addAttribute("query", displayQuery);
        model.addAttribute("totalCount", bookPage.getTotalElements() + communityPage.getTotalElements());
        model.addAttribute("currentUserId", currentUser != null ? currentUser.getId() : null);
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

    private Page<ResellListing> findCommunity(List<String> listingTypes, String normalizedQuery, int pageNumber) {
        if (listingTypes.isEmpty()) {
            return Page.empty();
        }
        return resellListings.findAll(communityFilter(listingTypes, normalizedQuery),
                PageRequest.of(pageNumber, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    /**
     * Loads the requested page; if it lies past the last page, loads the last page instead. Empty results keep page 0.
     */
    static <T> Page<T> pageClamped(int requested, IntFunction<Page<T>> loader) {
        Page<T> result = loader.apply(requested);
        if (requested > 0 && requested >= result.getTotalPages()) {
            result = loader.apply(Math.max(result.getTotalPages() - 1, 0));
        }
        return result;
    }

    /** Search text is trimmed and capped so a long query cannot build an oversized LIKE pattern. */
    static String clampQuery(String q) {
        if (q == null) {
            return "";
        }
        String trimmed = q.trim();
        return trimmed.length() > MAX_QUERY_LENGTH ? trimmed.substring(0, MAX_QUERY_LENGTH).trim() : trimmed;
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

    /** AVAILABLE community listings of the given types, matching title, author or desired book when a query is set. */
    static Specification<ResellListing> communityFilter(List<String> listingTypes, String normalizedQuery) {
        Specification<ResellListing> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), "AVAILABLE"),
                root.get("listingType").in(listingTypes));
        if (!normalizedQuery.isEmpty()) {
            String pattern = "%" + escapeLike(normalizedQuery) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), pattern, '\\'),
                    cb.like(cb.lower(root.get("author")), pattern, '\\'),
                    cb.like(cb.lower(root.get("desiredBook")), pattern, '\\')));
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

package com.example.EPustok.market;

import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogControllerTest {

    /** Community listings come from the database already filtered and paged; the stub returns one page of them. */
    private static ResellListings communityReturns(ResellListing... items) {
        ResellListings listings = mock(ResellListings.class);
        when(listings.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(items), inv.<Pageable>getArgument(1), items.length));
        return listings;
    }

    @Test
    void everyUserSeesSameSellerListedBooksAndCommunityListings() {
        Books books = mock(Books.class);
        Categories categories = mock(Categories.class);
        SellerProfiles profiles = mock(SellerProfiles.class);
        UserRepository users = mock(UserRepository.class);

        Category fiction = new Category("Fiction");
        Book book1 = new Book(10L);
        book1.update("The Quiet Atlas", "Ella Morrow", new BigDecimal("450.00"), 5, fiction);
        Book book2 = new Book(11L);
        book2.update("Field Notes", "J. Rahman", new BigDecimal("320.00"), 2, fiction);

        ResellListing resell = new ResellListing(20L, "RESELL", "Used Dune", "Frank Herbert", "GOOD",
                new BigDecimal("180.00"), null, "Clean copy");
        ResellListing exchange = new ResellListing(21L, "EXCHANGE", "1984", "George Orwell", "LIKE_NEW",
                null, "Brave New World", "Open to swap");
        ResellListings resellListings = communityReturns(resell, exchange);

        when(books.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(book1, book2)));
        when(categories.findAll()).thenReturn(List.of(fiction));
        when(profiles.findAllById(anyIterable())).thenReturn(List.of(
                new SellerProfile(10L, "Leaf & Spine", "01700000000", "Dhaka"),
                new SellerProfile(11L, "Paperhouse", "01800000000", "Chattogram")
        ));
        when(users.findNamesByIdIn(anyCollection())).thenReturn(List.of(name(20L, "Reader"), name(21L, "Scholar")));

        CatalogController controller = new CatalogController(books, categories, profiles, resellListings, users);

        Model buyerModel = new ExtendedModelMap();
        String buyerView = controller.catalog(null, null, null, 0,
                new UsernamePasswordAuthenticationToken("buyer@example.com", "pw",
                        List.of(new SimpleGrantedAuthority("ROLE_BUYER"))),
                buyerModel);

        Model sellerModel = new ExtendedModelMap();
        String sellerView = controller.catalog(null, null, null, 0,
                new UsernamePasswordAuthenticationToken("seller@example.com", "pw",
                        List.of(new SimpleGrantedAuthority("ROLE_SELLER"))),
                sellerModel);

        Model adminModel = new ExtendedModelMap();
        String adminView = controller.catalog(null, null, null, 0,
                new UsernamePasswordAuthenticationToken("admin@example.com", "pw",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))),
                adminModel);

        assertEquals("catalog", buyerView);
        assertEquals("catalog", sellerView);
        assertEquals("catalog", adminView);
        assertEquals(List.of(book1, book2), buyerModel.getAttribute("books"));
        assertEquals(List.of(book1, book2), sellerModel.getAttribute("books"));
        assertEquals(List.of(book1, book2), adminModel.getAttribute("books"));
        assertEquals(List.of(resell, exchange), buyerModel.getAttribute("resellListings"));
    }

    @Test
    void modelHoldsOnlyDisplayNamesNeverUserEntitiesOrPasswordHashes() {
        Books books = mock(Books.class);
        SellerProfiles profiles = mock(SellerProfiles.class);
        UserRepository users = mock(UserRepository.class);

        Category fiction = new Category("Fiction");
        Book book = new Book(10L);
        book.update("The Quiet Atlas", "Ella Morrow", new BigDecimal("450.00"), 5, fiction);
        ResellListing resell = new ResellListing(20L, "RESELL", "Used Dune", "Frank Herbert", "GOOD",
                new BigDecimal("180.00"), null, "Clean copy");

        when(books.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(book)));
        when(profiles.findAllById(anyIterable())).thenReturn(List.of(
                new SellerProfile(10L, "Leaf & Spine", "01700000000", "Dhaka")));
        when(users.findNamesByIdIn(anyCollection())).thenReturn(List.of(name(20L, "Reader")));
        when(users.findByEmailIgnoreCase("buyer@example.com"))
                .thenReturn(Optional.of(new User("Buyer", "buyer@example.com", "secret-hash")));

        CatalogController controller = new CatalogController(books, mock(Categories.class), profiles,
                communityReturns(resell), users);
        Model model = new ExtendedModelMap();
        controller.catalog(null, null, null, 0,
                new UsernamePasswordAuthenticationToken("buyer@example.com", "pw",
                        List.of(new SimpleGrantedAuthority("ROLE_BUYER"))),
                model);

        verify(users, never()).findAll();
        verify(profiles, never()).findAll();
        assertEquals(Map.of(20L, "Reader"), model.getAttribute("listerNames"));
        assertEquals(Map.of(10L, "Leaf & Spine"), model.getAttribute("sellerNames"));
        for (Map.Entry<String, Object> attribute : model.asMap().entrySet()) {
            assertFalse(containsUser(attribute.getValue()),
                    "model attribute " + attribute.getKey() + " must not hold a User entity");
            assertFalse(String.valueOf(attribute.getValue()).contains("secret-hash"),
                    "model attribute " + attribute.getKey() + " must not expose a password hash");
        }
    }

    private static UserRepository.UserName name(long id, String value) {
        return new UserRepository.UserName() {
            @Override public Long getId() { return id; }
            @Override public String getName() { return value; }
        };
    }

    private static boolean containsUser(Object value) {
        if (value instanceof User) {
            return true;
        }
        if (value instanceof Map<?, ?> map) {
            return map.keySet().stream().anyMatch(CatalogControllerTest::containsUser)
                    || map.values().stream().anyMatch(CatalogControllerTest::containsUser);
        }
        if (value instanceof Iterable<?> items) {
            for (Object item : items) {
                if (containsUser(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void resellAndExchangeViewsShowOnlyCommunityListingsReturnedByTheDatabase() {
        Books books = mock(Books.class);
        Categories categories = mock(Categories.class);
        SellerProfiles profiles = mock(SellerProfiles.class);
        UserRepository users = mock(UserRepository.class);

        ResellListing resell = new ResellListing(20L, "RESELL", "Used Dune", "Frank Herbert", "GOOD",
                new BigDecimal("180.00"), null, "Clean copy");
        ResellListing exchange = new ResellListing(21L, "EXCHANGE", "1984", "George Orwell", "LIKE_NEW",
                null, "Brave New World", "Open to swap");
        ResellListings resellListings = mock(ResellListings.class);
        when(resellListings.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resell)))
                .thenReturn(new PageImpl<>(List.of(exchange)));

        CatalogController controller = new CatalogController(books, categories, profiles, resellListings, users);

        Model resellModel = new ExtendedModelMap();
        controller.catalog(null, "resell", null, 0,
                new UsernamePasswordAuthenticationToken("buyer@example.com", "pw"), resellModel);
        assertEquals(List.of(), resellModel.getAttribute("books"));
        assertEquals(List.of(resell), resellModel.getAttribute("resellListings"));
        verify(books, never()).findAll(any(Specification.class), any(Pageable.class));

        Model exchangeModel = new ExtendedModelMap();
        controller.catalog(null, "exchange", "brave", 0,
                new UsernamePasswordAuthenticationToken("buyer@example.com", "pw"), exchangeModel);
        assertEquals(List.of(), exchangeModel.getAttribute("books"));
        assertEquals(List.of(exchange), exchangeModel.getAttribute("resellListings"));
    }

    @Test
    void communityListingsAreRequestedNewestFirstInPagesOfTwelve() {
        ResellListings resellListings = mock(ResellListings.class);
        when(resellListings.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        CatalogController controller = new CatalogController(mock(Books.class), mock(Categories.class),
                mock(SellerProfiles.class), resellListings, mock(UserRepository.class));

        controller.catalog(null, "exchange", null, 0,
                new UsernamePasswordAuthenticationToken("buyer@example.com", "pw"), new ExtendedModelMap());

        org.mockito.ArgumentCaptor<Pageable> pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(resellListings).findAll(any(Specification.class), pageable.capture());
        assertEquals(PageRequest.of(0, CatalogController.PAGE_SIZE,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt", "id")),
                pageable.getValue());
    }
}

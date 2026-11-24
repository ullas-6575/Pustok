package com.example.EPustok.market;

import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CatalogPagingTest {
    private static User user(long id, String email) throws Exception {
        User user = new User("Reader", email, "hash");
        var field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
        return user;
    }

    private static Book book(long sellerId, int stock) {
        Book book = new Book(sellerId);
        book.update("The Quiet Atlas", "Ella Morrow", new BigDecimal("450.00"), stock, new Category("Fiction"));
        return book;
    }

    private static ResellListing listing(long id, String type) throws Exception {
        ResellListing listing = new ResellListing(20L, type, "Used Dune", "Frank Herbert", "GOOD",
                "RESELL".equals(type) ? new BigDecimal("180.00") : null, "Dune", "Clean copy");
        var field = ResellListing.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(listing, id);
        return listing;
    }

    /** Community repository that answers every page request with the given listings. */
    private static ResellListings community(List<ResellListing> items, long total) {
        ResellListings listings = mock(ResellListings.class);
        when(listings.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(inv -> {
            Pageable pageable = inv.getArgument(1);
            return new PageImpl<>(items, pageable, total);
        });
        return listings;
    }

    private static CatalogController controller(Books books, SellerProfiles profiles, UserRepository users, ResellListings listings) {
        return new CatalogController(books, mock(Categories.class), profiles, listings, users);
    }

    private static UsernamePasswordAuthenticationToken buyer() {
        return new UsernamePasswordAuthenticationToken("reader@example.com", "pw", List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
    }

    @Test void pagesNewBooksWithFixedPageSizeAndClampsNegativePage() {
        Books books = mock(Books.class);
        when(books.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        UserRepository users = mock(UserRepository.class);
        ExtendedModelMap model = new ExtendedModelMap();

        controller(books, mock(SellerProfiles.class), users, mock(ResellListings.class)).catalog(null, "new", null, -3,
                new UsernamePasswordAuthenticationToken("reader@example.com", "pw", List.of(new SimpleGrantedAuthority("ROLE_BUYER"))),
                model);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(books).findAll(any(Specification.class), pageable.capture());
        assertEquals(PageRequest.of(0, CatalogController.PAGE_SIZE, pageable.getValue().getSort()), pageable.getValue());
        assertEquals(12, pageable.getValue().getPageSize());
        assertEquals(0, pageable.getValue().getPageNumber());
    }

    @Test void reportsPageNavigationFromDatabasePage() throws Exception {
        Books books = mock(Books.class);
        Book first = book(10L, 3);
        Page<Book> page = new PageImpl<>(List.of(first), PageRequest.of(1, 12), 30);
        when(books.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmailIgnoreCase("reader@example.com")).thenReturn(Optional.of(user(5L, "reader@example.com")));
        ExtendedModelMap model = new ExtendedModelMap();

        controller(books, mock(SellerProfiles.class), users, community(List.of(), 0)).catalog(null, "all", null, 1,
                new UsernamePasswordAuthenticationToken("reader@example.com", "pw", List.of(new SimpleGrantedAuthority("ROLE_BUYER"))),
                model);

        assertEquals(1, model.getAttribute("currentPage"));
        assertEquals(3, model.getAttribute("totalPages"));
        assertEquals(true, model.getAttribute("hasPrevious"));
        assertEquals(true, model.getAttribute("hasNext"));
        assertEquals(30L + 0, model.getAttribute("totalCount"));
    }

    /** MKT-2: community cards appear on the first page of the mixed view only, but still count toward the total. */
    @Test void communityCardsShowOnlyOnFirstPageOfMixedCatalog() throws Exception {
        Books books = mock(Books.class);
        Book first = book(10L, 3);
        when(books.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(inv -> {
            Pageable pageable = inv.getArgument(1);
            return new PageImpl<>(List.of(first), pageable, 30);
        });
        ResellListing resell = listing(20L, "RESELL");
        UserRepository users = mock(UserRepository.class);
        when(users.findNamesByIdIn(any())).thenReturn(List.of());

        ExtendedModelMap firstPage = new ExtendedModelMap();
        controller(books, mock(SellerProfiles.class), users, community(List.of(resell), 1))
                .catalog(null, "all", null, 0, buyer(), firstPage);
        assertEquals(List.of(resell), firstPage.getAttribute("resellListings"));
        assertEquals(31L, firstPage.getAttribute("totalCount"));

        ExtendedModelMap secondPage = new ExtendedModelMap();
        controller(books, mock(SellerProfiles.class), users, community(List.of(resell), 1))
                .catalog(null, "all", null, 1, buyer(), secondPage);
        assertEquals(List.of(), secondPage.getAttribute("resellListings"));
        assertEquals(1, secondPage.getAttribute("currentPage"));
        assertEquals(31L, secondPage.getAttribute("totalCount"));
    }

    /** MKT-2: a page past the last book page is clamped to the last page instead of showing an empty grid. */
    @Test void pageBeyondLastBookPageIsClampedToLastPage() throws Exception {
        Books books = mock(Books.class);
        Book first = book(10L, 3);
        when(books.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(inv -> {
            Pageable pageable = inv.getArgument(1);
            return pageable.getPageNumber() == 9
                    ? new PageImpl<>(List.of(), pageable, 30)
                    : new PageImpl<>(List.of(first), pageable, 30);
        });
        ExtendedModelMap model = new ExtendedModelMap();

        controller(books, mock(SellerProfiles.class), mock(UserRepository.class), mock(ResellListings.class))
                .catalog(null, "new", null, 9, buyer(), model);

        ArgumentCaptor<Pageable> requested = ArgumentCaptor.forClass(Pageable.class);
        verify(books, times(2)).findAll(any(Specification.class), requested.capture());
        assertEquals(9, requested.getAllValues().get(0).getPageNumber());
        assertEquals(2, requested.getAllValues().get(1).getPageNumber());
        assertEquals(2, model.getAttribute("currentPage"));
        assertEquals(List.of(first), model.getAttribute("books"));
        assertEquals(true, model.getAttribute("hasPrevious"));
        assertEquals(false, model.getAttribute("hasNext"));
    }

    /** Resell and Exchange views page through community listings from the database. */
    @Test void resellOnlyViewPagesCommunityListings() throws Exception {
        ResellListings listings = mock(ResellListings.class);
        ResellListing second = listing(31L, "RESELL");
        when(listings.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(inv -> {
            Pageable pageable = inv.getArgument(1);
            return new PageImpl<>(pageable.getPageNumber() == 1 ? List.of(second) : List.of(), pageable, 13);
        });
        ExtendedModelMap model = new ExtendedModelMap();

        controller(mock(Books.class), mock(SellerProfiles.class), mock(UserRepository.class), listings)
                .catalog(null, "resell", null, 1, buyer(), model);

        assertEquals(List.of(second), model.getAttribute("resellListings"));
        assertEquals(1, model.getAttribute("currentPage"));
        assertEquals(2, model.getAttribute("totalPages"));
        assertEquals(13L, model.getAttribute("totalCount"));
    }

    /** MKT-7: the search string is capped server-side at 100 characters. */
    @Test void searchTextIsCappedAtOneHundredCharacters() {
        Books books = mock(Books.class);
        when(books.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        ExtendedModelMap model = new ExtendedModelMap();

        controller(books, mock(SellerProfiles.class), mock(UserRepository.class), mock(ResellListings.class))
                .catalog(null, "new", "  " + "a".repeat(150), 0, buyer(), model);

        assertEquals(100, ((String) model.getAttribute("query")).length());
    }

    @Test void unknownBookDetailIsNotFound() {
        Books books = mock(Books.class);
        when(books.findById(404L)).thenReturn(Optional.empty());
        var controller = controller(books, mock(SellerProfiles.class), mock(UserRepository.class), mock(ResellListings.class));
        var auth = new UsernamePasswordAuthenticationToken("reader@example.com", "pw", List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
        var ex = assertThrows(ResponseStatusException.class, () -> controller.bookDetail(404L, auth, new ExtendedModelMap()));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test void ownerSeesNoCartActionAndBuyerSeesCartOnlyWhenInStock() throws Exception {
        Books books = mock(Books.class);
        Book inStock = book(10L, 2);
        Book soldOut = book(10L, 0);
        when(books.findById(1L)).thenReturn(Optional.of(inStock));
        when(books.findById(2L)).thenReturn(Optional.of(soldOut));
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmailIgnoreCase("seller@example.com")).thenReturn(Optional.of(user(10L, "seller@example.com")));
        when(users.findByEmailIgnoreCase("buyer@example.com")).thenReturn(Optional.of(user(20L, "buyer@example.com")));
        SellerProfiles profiles = mock(SellerProfiles.class);
        when(profiles.findById(10L)).thenReturn(Optional.of(new SellerProfile(10L, "Leaf & Spine", "01700000000", "Dhaka")));
        var controller = controller(books, profiles, users, mock(ResellListings.class));
        var seller = new UsernamePasswordAuthenticationToken("seller@example.com", "pw", List.of(new SimpleGrantedAuthority("ROLE_SELLER")));
        var buyer = new UsernamePasswordAuthenticationToken("buyer@example.com", "pw", List.of(new SimpleGrantedAuthority("ROLE_BUYER")));

        ExtendedModelMap owner = new ExtendedModelMap();
        controller.bookDetail(1L, seller, owner);
        assertEquals(true, owner.getAttribute("isOwnBook"));
        assertEquals(false, owner.getAttribute("canAddToCart"));
        assertEquals("Leaf & Spine", owner.getAttribute("sellerName"));

        ExtendedModelMap reader = new ExtendedModelMap();
        controller.bookDetail(1L, buyer, reader);
        assertEquals(false, reader.getAttribute("isOwnBook"));
        assertEquals(true, reader.getAttribute("canAddToCart"));

        ExtendedModelMap outOfStock = new ExtendedModelMap();
        controller.bookDetail(2L, buyer, outOfStock);
        assertEquals(false, outOfStock.getAttribute("canAddToCart"));
    }
}

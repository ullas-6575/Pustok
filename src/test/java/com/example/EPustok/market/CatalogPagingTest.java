package com.example.EPustok.market;

import com.example.EPustok.cart.CartService;
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

    private static CatalogController controller(Books books, SellerProfiles profiles, UserRepository users) {
        return new CatalogController(books, mock(Categories.class), profiles, mock(ResellListings.class), users, mock(CartService.class));
    }

    @Test void pagesNewBooksWithFixedPageSizeAndClampsNegativePage() {
        Books books = mock(Books.class);
        when(books.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        UserRepository users = mock(UserRepository.class);
        ExtendedModelMap model = new ExtendedModelMap();

        controller(books, mock(SellerProfiles.class), users).catalog(null, "new", null, -3,
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

        controller(books, mock(SellerProfiles.class), users).catalog(null, "all", null, 1,
                new UsernamePasswordAuthenticationToken("reader@example.com", "pw", List.of(new SimpleGrantedAuthority("ROLE_BUYER"))),
                model);

        assertEquals(1, model.getAttribute("currentPage"));
        assertEquals(3, model.getAttribute("totalPages"));
        assertEquals(true, model.getAttribute("hasPrevious"));
        assertEquals(true, model.getAttribute("hasNext"));
        assertEquals(30L + 0, model.getAttribute("totalCount"));
    }

    @Test void unknownBookDetailIsNotFound() {
        Books books = mock(Books.class);
        when(books.findById(404L)).thenReturn(Optional.empty());
        var controller = controller(books, mock(SellerProfiles.class), mock(UserRepository.class));
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
        var controller = controller(books, profiles, users);
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

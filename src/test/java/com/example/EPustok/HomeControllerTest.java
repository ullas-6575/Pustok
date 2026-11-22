package com.example.EPustok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.EPustok.market.Book;
import com.example.EPustok.market.Books;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;

/** Home shows only database books; with none, the template renders its empty state. */
class HomeControllerTest {
    @Test void showsDatabaseBooksForSignedInUser() {
        Books books = mock(Books.class);
        Book book = new Book(10L);
        book.update("The Quiet Atlas", "Ella Morrow", new BigDecimal("450.00"), 3, new com.example.EPustok.market.Category("Fiction"));
        when(books.findAll()).thenReturn(List.of(book));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = new HomeController(books).home(new UsernamePasswordAuthenticationToken("reader@example.com", "pw"), model);

        assertEquals("home", view);
        assertEquals(List.of(book), model.getAttribute("books"));
        assertEquals("reader", model.getAttribute("displayName"));
    }

    @Test void emptyDatabaseProducesEmptyList() {
        Books books = mock(Books.class);
        when(books.findAll()).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        new HomeController(books).home(new UsernamePasswordAuthenticationToken("reader@example.com", "pw"), model);

        assertTrue(((List<?>) model.getAttribute("books")).isEmpty());
    }
}

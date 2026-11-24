package com.example.EPustok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.EPustok.market.Book;
import com.example.EPustok.market.Books;
import com.example.EPustok.market.Categories;
import com.example.EPustok.market.Category;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;

/** Home shows the newest database books and the real categories; with none, the template renders its empty states. */
class HomeControllerTest {
    @Test void showsNewestDatabaseBooksAndCategoriesForSignedInUser() {
        Books books = mock(Books.class);
        Categories categories = mock(Categories.class);
        Category fiction = new Category("Fiction");
        Book book = new Book(10L);
        book.update("The Quiet Atlas", "Ella Morrow", new BigDecimal("450.00"), 3, fiction);
        when(books.findTop12ByOrderByIdDesc()).thenReturn(List.of(book));
        when(categories.findAll()).thenReturn(List.of(fiction));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = new HomeController(books, categories).home(new UsernamePasswordAuthenticationToken("reader@example.com", "pw"), model);

        assertEquals("home", view);
        assertEquals(List.of(book), model.getAttribute("books"));
        assertEquals(List.of(fiction), model.getAttribute("categories"));
        assertEquals("reader", model.getAttribute("displayName"));
    }

    @Test void homeNeverLoadsTheWholeBookTable() {
        Books books = mock(Books.class);
        Categories categories = mock(Categories.class);
        when(books.findTop12ByOrderByIdDesc()).thenReturn(List.of());
        when(categories.findAll()).thenReturn(List.of());

        new HomeController(books, categories).home(new UsernamePasswordAuthenticationToken("reader@example.com", "pw"), new ExtendedModelMap());

        verify(books, never()).findAll();
    }

    @Test void emptyDatabaseProducesEmptyLists() {
        Books books = mock(Books.class);
        Categories categories = mock(Categories.class);
        when(books.findTop12ByOrderByIdDesc()).thenReturn(List.of());
        when(categories.findAll()).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        new HomeController(books, categories).home(new UsernamePasswordAuthenticationToken("reader@example.com", "pw"), model);

        assertTrue(((List<?>) model.getAttribute("books")).isEmpty());
        assertTrue(((List<?>) model.getAttribute("categories")).isEmpty());
    }
}

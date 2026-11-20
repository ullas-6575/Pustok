package com.example.EPustok.market;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogControllerTest {

    @Test
    void everyUserSeesSameSellerListedBooks() {
        Books books = mock(Books.class);
        Categories categories = mock(Categories.class);
        SellerProfiles profiles = mock(SellerProfiles.class);

        Category fiction = new Category("Fiction");
        Book book1 = new Book(10L);
        book1.update("The Quiet Atlas", "Ella Morrow", new BigDecimal("450.00"), 5, fiction);
        Book book2 = new Book(11L);
        book2.update("Field Notes", "J. Rahman", new BigDecimal("320.00"), 2, fiction);

        when(books.findAll()).thenReturn(List.of(book1, book2));
        when(categories.findAll()).thenReturn(List.of(fiction));
        when(profiles.findAll()).thenReturn(List.of(
                new SellerProfile(10L, "Leaf & Spine", "01700000000", "Dhaka"),
                new SellerProfile(11L, "Paperhouse", "01800000000", "Chattogram")
        ));

        CatalogController controller = new CatalogController(books, categories, profiles);

        Model buyerModel = new ExtendedModelMap();
        String buyerView = controller.catalog(null, null,
                new UsernamePasswordAuthenticationToken("buyer@example.com", "pw"), buyerModel);

        Model sellerModel = new ExtendedModelMap();
        String sellerView = controller.catalog(null, null,
                new UsernamePasswordAuthenticationToken("seller@example.com", "pw"), sellerModel);

        Model adminModel = new ExtendedModelMap();
        String adminView = controller.catalog(null, null,
                new UsernamePasswordAuthenticationToken("admin@example.com", "pw"), adminModel);

        assertEquals("catalog", buyerView);
        assertEquals("catalog", sellerView);
        assertEquals("catalog", adminView);
        assertEquals(List.of(book1, book2), buyerModel.getAttribute("books"));
        assertEquals(List.of(book1, book2), sellerModel.getAttribute("books"));
        assertEquals(List.of(book1, book2), adminModel.getAttribute("books"));
    }

    @Test
    void filtersBySearchQuery() {
        Books books = mock(Books.class);
        Categories categories = mock(Categories.class);
        SellerProfiles profiles = mock(SellerProfiles.class);

        Category fiction = new Category("Fiction");
        Book book1 = new Book(10L);
        book1.update("The Quiet Atlas", "Ella Morrow", new BigDecimal("450.00"), 5, fiction);
        Book book2 = new Book(11L);
        book2.update("Field Notes", "J. Rahman", new BigDecimal("320.00"), 2, fiction);

        when(books.findAll()).thenReturn(List.of(book1, book2));
        when(categories.findAll()).thenReturn(List.of(fiction));
        when(profiles.findAll()).thenReturn(List.of());

        CatalogController controller = new CatalogController(books, categories, profiles);
        Model model = new ExtendedModelMap();

        controller.catalog(null, "atlas",
                new UsernamePasswordAuthenticationToken("buyer@example.com", "pw"), model);

        assertEquals(List.of(book1), model.getAttribute("books"));
    }
}

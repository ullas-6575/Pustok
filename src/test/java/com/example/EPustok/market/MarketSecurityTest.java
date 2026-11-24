package com.example.EPustok.market;

import com.example.EPustok.identity.JwtService;
import com.example.EPustok.identity.SecurityConfig;
import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Admin category rules and seller book ownership, enforced through the real security chain and services.
 * Role gates in SecurityConfig answer 403 before any controller runs. Rule failures inside the HTML form handlers
 * redirect back to the page with the reason flashed as an error.
 */
@SpringJUnitConfig(MarketSecurityTest.Config.class)
@WebAppConfiguration
class MarketSecurityTest {
    @Configuration @EnableWebMvc @Import(SecurityConfig.class)
    static class Config {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean JdbcTemplate jdbc() { return mock(JdbcTemplate.class); }
        @Bean JwtService tokens(JdbcTemplate jdbc) { return new JwtService(Base64.getEncoder().encodeToString(new byte[32]), false, jdbc); }
        @Bean SellerProfiles profiles() { return mock(SellerProfiles.class); }
        @Bean Categories categories() { return mock(Categories.class); }
        @Bean Books books() { return mock(Books.class); }
        @Bean ResellListings resellListings() { return mock(ResellListings.class); }
        @Bean ExchangeOffers exchangeOffers() { return mock(ExchangeOffers.class); }
        @Bean MarketService marketService(UserRepository users, SellerProfiles profiles, ResellListings listings, ExchangeOffers offers) { return new MarketService(users, profiles, listings, offers); }
        @Bean MarketController marketController(SellerProfiles profiles, Categories categories, Books books, UserRepository users, MarketService service) {
            return new MarketController(profiles, categories, books, users, service);
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JwtService tokens;
    @Autowired SellerProfiles profiles;
    @Autowired Categories categories;
    @Autowired Books books;
    MockMvc mvc;

    private static User user(long id, String role, String email) throws Exception {
        User user = new User(role, email, "hash");
        if (!role.equals("BUYER")) user.assignRole(com.example.EPustok.identity.Role.valueOf(role));
        var field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
        return user;
    }

    private static Book book(long sellerId, long id) throws Exception {
        Book book = new Book(sellerId);
        book.update("Old title", "Author", BigDecimal.TEN, 2, new Category("Fiction"));
        var field = Book.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(book, id);
        return book;
    }

    private String bearer(String email) { return "Bearer " + tokens.issue(email); }

    @BeforeEach void setup() throws Exception {
        reset(users, profiles, categories, books);
        User admin = user(1L, "ADMIN", "admin@example.com");
        User buyer = user(2L, "BUYER", "buyer@example.com");
        User sellerA = user(10L, "SELLER", "seller-a@example.com");
        User sellerB = user(11L, "SELLER", "seller-b@example.com");
        User pendingSeller = user(12L, "SELLER", "pending@example.com");
        for (User u : List.of(admin, buyer, sellerA, sellerB, pendingSeller)) {
            when(users.findByEmailIgnoreCase(u.getEmail())).thenReturn(Optional.of(u));
        }
        SellerProfile approvedA = new SellerProfile(10L, "Shop A", "01700000000", "Dhaka");
        approvedA.decide(true);
        SellerProfile approvedB = new SellerProfile(11L, "Shop B", "01800000000", "Chattogram");
        approvedB.decide(true);
        when(profiles.findById(10L)).thenReturn(Optional.of(approvedA));
        when(profiles.findById(11L)).thenReturn(Optional.of(approvedB));
        when(profiles.findById(12L)).thenReturn(Optional.of(new SellerProfile(12L, "Pending", "01900000000", "Sylhet")));
        when(categories.findById(5L)).thenReturn(Optional.of(new Category("Fiction")));
        when(books.findById(100L)).thenReturn(Optional.of(book(10L, 100L)));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void buyerCannotChangeCategories() throws Exception {
        mvc.perform(post("/admin/categories").with(csrf()).header("Authorization", bearer("buyer@example.com")).param("name", "Poetry"))
                .andExpect(status().isForbidden());
        verify(categories, never()).save(any(Category.class));
    }

    @Test void adminCreatesAndDeletesEmptyCategory() throws Exception {
        mvc.perform(post("/admin/categories").with(csrf()).header("Authorization", bearer("admin@example.com")).param("name", "Poetry"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/admin/categories"))
                .andExpect(flash().attribute("message", "Category added."));
        verify(categories).save(any(Category.class));

        when(categories.existsById(7L)).thenReturn(true);
        when(books.existsByCategoryId(7L)).thenReturn(false);
        mvc.perform(post("/admin/categories/7/delete").with(csrf()).header("Authorization", bearer("admin@example.com")))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/admin/categories"));
        verify(categories).deleteById(7L);
    }

    @Test void categoryContainingBooksCannotBeDeleted() throws Exception {
        when(categories.existsById(7L)).thenReturn(true);
        when(books.existsByCategoryId(7L)).thenReturn(true);
        mvc.perform(post("/admin/categories/7/delete").with(csrf()).header("Authorization", bearer("admin@example.com")))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/admin/categories"))
                .andExpect(flash().attribute("error", "Category contains books"));
        verify(categories, never()).deleteById(any());
    }

    /** MKT-5: deleting a category that does not exist is flashed as not found, not a raw 404 page. */
    @Test void deletingMissingCategoryIsFlashedNotFound() throws Exception {
        when(categories.existsById(99L)).thenReturn(false);
        mvc.perform(post("/admin/categories/99/delete").with(csrf()).header("Authorization", bearer("admin@example.com")))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/admin/categories"))
                .andExpect(flash().attribute("error", "Category not found"));
        verify(books, never()).existsByCategoryId(any());
        verify(categories, never()).deleteById(any());
    }

    /** MKT-5: a second category with the same name is refused before saving. */
    @Test void duplicateCategoryNameIsRefusedAndFlashed() throws Exception {
        when(categories.existsByName("Poetry")).thenReturn(true);
        mvc.perform(post("/admin/categories").with(csrf()).header("Authorization", bearer("admin@example.com")).param("name", "Poetry"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/admin/categories"))
                .andExpect(flash().attribute("error", "Category name already exists"));
        verify(categories, never()).save(any(Category.class));
    }

    /** MKT-5: renaming to a name another category already has is refused; keeping the category's own name is not. */
    @Test void renamingToAnotherCategoriesNameIsRefused() throws Exception {
        when(categories.existsByNameAndIdNot("Literature", 5L)).thenReturn(true);
        mvc.perform(post("/admin/categories").with(csrf()).header("Authorization", bearer("admin@example.com"))
                        .param("id", "5").param("name", "Literature"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/admin/categories"))
                .andExpect(flash().attribute("error", "Category name already exists"));
        verify(categories, never()).save(any(Category.class));
    }

    @Test void sellerWithoutApprovedProfileCannotListOrCreateBooks() throws Exception {
        mvc.perform(get("/seller/books").header("Authorization", bearer("pending@example.com")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/seller/books").with(csrf()).header("Authorization", bearer("pending@example.com"))
                        .param("title", "T").param("author", "A").param("price", "10.00").param("stock", "1").param("categoryId", "5"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/seller/books"))
                .andExpect(flash().attributeExists("error"));
        verify(books, never()).save(any(Book.class));
    }

    @Test void approvedSellerCanEditOwnBookButNotAnotherSellersBook() throws Exception {
        mvc.perform(post("/seller/books").with(csrf()).param("id", "100").header("Authorization", bearer("seller-a@example.com"))
                        .param("title", "New title").param("author", "A").param("price", "12.00").param("stock", "4").param("categoryId", "5"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/seller/books"));
        verify(books).save(any(Book.class));

        mvc.perform(post("/seller/books").with(csrf()).param("id", "100").header("Authorization", bearer("seller-b@example.com"))
                        .param("title", "Hijack").param("author", "A").param("price", "1.00").param("stock", "1").param("categoryId", "5"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/seller/books"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/seller/books/100/delete").with(csrf()).header("Authorization", bearer("seller-b@example.com")))
                .andExpect(status().isFound()).andExpect(flash().attributeExists("error"));
        verify(books, times(1)).save(any(Book.class));
        verify(books, never()).delete(any(Book.class));
    }

    @Test void buyerCanApplyButSellerCannotReapply() throws Exception {
        when(profiles.findById(2L)).thenReturn(Optional.empty());
        mvc.perform(post("/buyer/seller-application").with(csrf()).header("Authorization", bearer("buyer@example.com"))
                        .param("shopName", "Reader Books").param("phone", "01700000001").param("address", "Dhaka"))
                .andExpect(status().isFound());
        verify(profiles).saveAndFlush(any(SellerProfile.class));

        mvc.perform(post("/buyer/seller-application").with(csrf()).header("Authorization", bearer("seller-a@example.com"))
                        .param("shopName", "Again").param("phone", "01700000002").param("address", "Dhaka"))
                .andExpect(status().isForbidden());
    }

    @Test void buyerCannotDecideSellerApplications() throws Exception {
        mvc.perform(post("/admin/applications/12").with(csrf()).header("Authorization", bearer("buyer@example.com")).param("approve", "true"))
                .andExpect(status().isForbidden());
        assertEquals("PENDING", profiles.findById(12L).orElseThrow().getStatus());
    }

    @Test void adminApprovesBuyerApplicationAndGrantsSellerRole() throws Exception {
        User applicant = user(13L, "BUYER", "applicant@example.com");
        SellerProfile application = new SellerProfile(13L, "Applicant Shop", "01600000000", "Rajshahi");
        when(users.findById(13L)).thenReturn(Optional.of(applicant));
        when(profiles.findById(13L)).thenReturn(Optional.of(application));
        mvc.perform(post("/admin/applications/13").with(csrf()).header("Authorization", bearer("admin@example.com")).param("approve", "true"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/admin/applications"))
                .andExpect(flash().attribute("message", "Seller application approved."));
        assertEquals("APPROVED", application.getStatus());
        assertEquals("SELLER", applicant.getRole());
    }

    @Test void adminDecisionOnUnknownOrAlreadyReviewedApplicationFailsWithFlash() throws Exception {
        mvc.perform(post("/admin/applications/99").with(csrf()).header("Authorization", bearer("admin@example.com")).param("approve", "true"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/admin/applications"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/admin/applications/10").with(csrf()).header("Authorization", bearer("admin@example.com")).param("approve", "false"))
                .andExpect(status().isFound()).andExpect(flash().attribute("error", "Application already reviewed"));
        assertEquals("APPROVED", profiles.findById(10L).orElseThrow().getStatus());
    }

    @Test void buyerCannotReachSellerBookRoutes() throws Exception {
        mvc.perform(get("/seller/books").header("Authorization", bearer("buyer@example.com"))).andExpect(status().isForbidden());
        mvc.perform(post("/seller/books").with(csrf()).header("Authorization", bearer("buyer@example.com"))
                        .param("title", "T").param("author", "A").param("price", "10.00").param("stock", "1").param("categoryId", "5"))
                .andExpect(status().isForbidden());
        verify(books, never()).save(any(Book.class));
    }

    @Test void sellerBookNotFoundForUnknownIdIsFlashed() throws Exception {
        mvc.perform(post("/seller/books").with(csrf()).param("id", "999").header("Authorization", bearer("seller-a@example.com"))
                        .param("title", "T").param("author", "A").param("price", "10.00").param("stock", "1").param("categoryId", "5"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/seller/books"))
                .andExpect(flash().attribute("error", "Book not found"));
        mvc.perform(post("/seller/books/999/delete").with(csrf()).header("Authorization", bearer("seller-a@example.com")))
                .andExpect(status().isFound()).andExpect(flash().attribute("error", "Book not found"));
        verify(books, never()).save(any(Book.class));
        verify(books, never()).delete(any(Book.class));
    }

    @Test void ownerDeletesOwnBook() throws Exception {
        mvc.perform(post("/seller/books/100/delete").with(csrf()).header("Authorization", bearer("seller-a@example.com")))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/seller/books"))
                .andExpect(flash().attribute("message", "Book removed from your shelf."));
        verify(books).delete(any(Book.class));
    }

    @Test void bookFormRejectsInvalidPriceWithoutSavingAndFlashesReason() throws Exception {
        mvc.perform(post("/seller/books").with(csrf()).header("Authorization", bearer("seller-a@example.com"))
                        .param("title", "T").param("author", "A").param("price", "0").param("stock", "1").param("categoryId", "5"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/seller/books"))
                .andExpect(flash().attributeExists("error"));
        verify(books, never()).save(any(Book.class));
    }

    @Test void adminRenamesCategoryAndRejectsUnknownOrBlankName() throws Exception {
        mvc.perform(post("/admin/categories").with(csrf()).header("Authorization", bearer("admin@example.com"))
                        .param("id", "5").param("name", "Literature"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/admin/categories"));
        verify(categories).save(any(Category.class));

        mvc.perform(post("/admin/categories").with(csrf()).header("Authorization", bearer("admin@example.com"))
                        .param("id", "77").param("name", "Ghost"))
                .andExpect(status().isFound()).andExpect(flash().attribute("error", "Category not found"));
        mvc.perform(post("/admin/categories").with(csrf()).header("Authorization", bearer("admin@example.com"))
                        .param("name", " "))
                .andExpect(status().isFound()).andExpect(flash().attributeExists("error"));
        verify(categories, times(1)).save(any(Category.class));
    }
}

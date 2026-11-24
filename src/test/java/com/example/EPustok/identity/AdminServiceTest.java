package com.example.EPustok.identity;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AdminService admin = new AdminService(users, jdbc);

    private static User userWithRole(Role role) {
        User user = new User("Person", "person@example.com", "hash");
        user.assignRole(role);
        return user;
    }

    @Test void demotingApprovedSellerResetsSellerProfileSoTheyCanReapply() {
        User seller = userWithRole(Role.SELLER);
        when(users.findById(7L)).thenReturn(Optional.of(seller));
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        admin.changeRole(7L, Role.BUYER);

        assertEquals("BUYER", seller.getRole());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), eq(7L));
        assertTrue(sql.getValue().contains("seller_profile"));
        assertTrue(sql.getValue().contains("'REJECTED'"));
    }

    @Test void promotingBuyerToSellerIsStillRefused() {
        when(users.findById(7L)).thenReturn(Optional.of(userWithRole(Role.BUYER)));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> admin.changeRole(7L, Role.SELLER));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test void buyerDemotionDoesNotTouchSellerProfile() {
        when(users.findById(8L)).thenReturn(Optional.of(userWithRole(Role.BUYER)));
        admin.changeRole(8L, Role.BUYER);
        verifyNoInteractions(jdbc);
    }

    @Test void adminAccountsCannotBeDisabledOrChangedHere() {
        when(users.findById(1L)).thenReturn(Optional.of(userWithRole(Role.ADMIN)));
        assertThrows(ResponseStatusException.class, () -> admin.setEnabled(1L, false));
        assertThrows(ResponseStatusException.class, () -> admin.changeRole(1L, Role.BUYER));
        verify(users, never()).save(any(User.class));
    }

    @Test void disablingBuyerPersistsFlagWithoutTokenRevocation() {
        User buyer = userWithRole(Role.BUYER);
        when(users.findById(2L)).thenReturn(Optional.of(buyer));
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminService.AccountView view = admin.setEnabled(2L, false);

        assertFalse(buyer.isEnabled());
        assertFalse(view.enabled());
        verifyNoInteractions(jdbc);
    }

    @Test void accountListingClampsSizeAndPage() {
        when(users.findAll(any(Pageable.class))).thenReturn(Page.empty());
        admin.accounts(0, 500);
        admin.accounts(999_999, 0);

        ArgumentCaptor<Pageable> pages = ArgumentCaptor.forClass(Pageable.class);
        verify(users, times(2)).findAll(pages.capture());
        assertEquals(100, pages.getAllValues().get(0).getPageSize());
        assertEquals(AdminService.MAX_PAGE, pages.getAllValues().get(1).getPageNumber());
        assertEquals(1, pages.getAllValues().get(1).getPageSize());
    }

    @Test void negativePageIsRejected() {
        assertThrows(ResponseStatusException.class, () -> admin.accounts(-1, 50));
    }
}

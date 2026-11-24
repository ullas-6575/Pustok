package com.example.EPustok.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PaymentControllerTest {

    @Test void unknownGatewayKeyRedirectsWithMessageAndSavesNothing() throws Exception {
        var store = new PaymentServiceTest.InMemoryPaymentStore();
        var service = new PaymentService(store, new PaymentGatewayFactory(), buyerRepo());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PaymentController(service)).build();
        UUID order = UUID.randomUUID();

        mvc.perform(post("/buyer/orders/{id}/pay", order)
                        .principal(new UsernamePasswordAuthenticationToken("buyer@example.com", "pw"))
                        .param("gateway", "PAYPAL")
                        .param("idempotencyKey", "key-1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/buyer/orders/" + order))
                .andExpect(flash().attribute("message", "Unknown payment gateway: PAYPAL"));

        assertTrue(store.payments.isEmpty());
    }

    @Test void mockGatewayIsRejectedWithMessageWhenDisabled() throws Exception {
        var store = new PaymentServiceTest.InMemoryPaymentStore();
        var service = new PaymentService(store, new PaymentGatewayFactory(false), buyerRepo());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PaymentController(service)).build();
        UUID order = UUID.randomUUID();
        store.addOrder(order, 7L, new BigDecimal("251.00"), "PLACED");

        mvc.perform(post("/buyer/orders/{id}/pay", order)
                        .principal(new UsernamePasswordAuthenticationToken("buyer@example.com", "pw"))
                        .param("gateway", "MOCK")
                        .param("idempotencyKey", "key-1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "Unsupported payment method"));

        assertTrue(store.payments.isEmpty());
        assertEquals("PLACED", store.orders.get(order).status());
    }

    private static UserRepository buyerRepo() throws Exception {
        var repo = mock(UserRepository.class);
        var user = new User("Name", "buyer@example.com", "hash");
        Field id = User.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(user, 7L);
        when(repo.findByEmailIgnoreCase("buyer@example.com")).thenReturn(Optional.of(user));
        return repo;
    }
}

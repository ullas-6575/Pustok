package com.example.EPustok.order;
import com.example.EPustok.identity.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ShipmentServiceTest {
@Test void sellerUpdateIsScopedAndCannotConfirmDelivery() throws Exception {
var jdbc=mock(JdbcTemplate.class);var users=mock(UserRepository.class);var user=new User("Seller","s@example.com","hash");var field=User.class.getDeclaredField("id");field.setAccessible(true);field.set(user,7L);when(users.findByEmailIgnoreCase("s@example.com")).thenReturn(Optional.of(user));
UUID id=UUID.randomUUID();var service=new ShipmentService(jdbc,users);
when(jdbc.update("UPDATE seller_shipment SET status=? WHERE order_id=? AND seller_id=? AND status=?","PROCESSING",id,7L,"PLACED")).thenReturn(1);
service.advance("s@example.com",id,OrderTracking.Status.PROCESSING);
verify(jdbc).update("UPDATE seller_shipment SET status=? WHERE order_id=? AND seller_id=? AND status=?","PROCESSING",id,7L,"PLACED");
assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.advance("s@example.com",id,OrderTracking.Status.DELIVERED));
assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.advance("s@example.com",id,OrderTracking.Status.SHIPPED));
}
}

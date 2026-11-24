package com.example.EPustok.order;
import com.example.EPustok.identity.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ShipmentServiceTest {
private static final String LOCK="SELECT id FROM purchase_order WHERE id=? FOR UPDATE";
private static final String PAID="SELECT COUNT(*) FROM payment_payments WHERE order_id=? AND (status='COMPLETED' OR gateway='COD')";
private static final String SHIP="UPDATE seller_shipment SET status=? WHERE order_id=? AND seller_id=? AND status=?";

@Test void sellerUpdateIsScopedAndCannotConfirmDelivery() throws Exception {
var jdbc=mock(JdbcTemplate.class);var users=sellerRepo();
UUID id=UUID.randomUUID();var service=new ShipmentService(jdbc,users);
lockExists(jdbc,id);
paid(jdbc,id,1);
when(jdbc.update(SHIP,"PROCESSING",id,7L,"PLACED")).thenReturn(1);
service.advance("s@example.com",id,OrderTracking.Status.PROCESSING);
verify(jdbc).update(SHIP,"PROCESSING",id,7L,"PLACED");
assertThrows(ResponseStatusException.class,()->service.advance("s@example.com",id,OrderTracking.Status.DELIVERED));
assertThrows(ResponseStatusException.class,()->service.advance("s@example.com",id,OrderTracking.Status.SHIPPED));
}

@Test void unpaidOrderCannotMoveToProcessing() throws Exception {
var jdbc=mock(JdbcTemplate.class);UUID id=UUID.randomUUID();var service=new ShipmentService(jdbc,sellerRepo());
lockExists(jdbc,id);
paid(jdbc,id,0);
var ex=assertThrows(ResponseStatusException.class,()->service.advance("s@example.com",id,OrderTracking.Status.PROCESSING));
assertEquals(HttpStatus.CONFLICT,ex.getStatusCode());
assertEquals("The order has not been paid yet",ex.getReason());
assertNoUpdates(jdbc);
}

@Test void adminCannotShipUnpaidOrder() throws Exception {
var jdbc=mock(JdbcTemplate.class);UUID id=UUID.randomUUID();var service=new ShipmentService(jdbc,sellerRepo());
lockExists(jdbc,id);
paid(jdbc,id,0);
var ex=assertThrows(ResponseStatusException.class,()->service.adminAdvance(id,7L,OrderTracking.Status.SHIPPED));
assertEquals(HttpStatus.CONFLICT,ex.getStatusCode());
assertNoUpdates(jdbc);
}

@Test void unknownOrderIsNotFoundInsteadOfServerError() throws Exception {
var jdbc=mock(JdbcTemplate.class);UUID id=UUID.randomUUID();var service=new ShipmentService(jdbc,sellerRepo());
when(jdbc.query(eq(LOCK),any(RowMapper.class),eq(id))).thenReturn(List.of());
var ex=assertThrows(ResponseStatusException.class,()->service.advance("s@example.com",id,OrderTracking.Status.PROCESSING));
assertEquals(HttpStatus.NOT_FOUND,ex.getStatusCode());
assertNoUpdates(jdbc);
}

@Test void sellerListExcludesCancelledShipments() throws Exception {
var jdbc=mock(JdbcTemplate.class);var service=new ShipmentService(jdbc,sellerRepo());
service.mine("s@example.com",0);
verify(jdbc).queryForList(contains("s.status<>'CANCELLED'"),eq(7L),eq(0));
}

private static void lockExists(JdbcTemplate jdbc,UUID id){
when(jdbc.query(eq(LOCK),any(RowMapper.class),eq(id))).thenReturn(List.of(id));
}

private static void paid(JdbcTemplate jdbc,UUID id,int count){
when(jdbc.queryForObject(PAID,Integer.class,id)).thenReturn(count);
}

private static void assertNoUpdates(JdbcTemplate jdbc){
assertTrue(mockingDetails(jdbc).getInvocations().stream().noneMatch(i->i.getMethod().getName().equals("update")));
}

private static UserRepository sellerRepo() throws Exception {
var user=new User("Seller","s@example.com","hash");
var field=User.class.getDeclaredField("id");field.setAccessible(true);field.set(user,7L);
var users=mock(UserRepository.class);
when(users.findByEmailIgnoreCase("s@example.com")).thenReturn(Optional.of(user));
return users;
}
}

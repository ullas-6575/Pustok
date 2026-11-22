package com.example.EPustok.order;
import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class OrderTrackingTest {
private static final String BUYER_EMAIL="buyer@example.com";
private static final String CANCEL_LOCK="SELECT status FROM purchase_order WHERE id=? AND buyer_id=? FOR UPDATE";
private static final String STARTED_COUNT="SELECT COUNT(*) FROM seller_shipment WHERE order_id=? AND status<>'PLACED'";
@Test void statusUpdateRequiresPreviousStage(){var jdbc=mock(JdbcTemplate.class);var service=new OrderTracking(jdbc,mock(UserRepository.class));UUID id=UUID.randomUUID();when(jdbc.queryForList("SELECT status FROM seller_shipment WHERE order_id=?",String.class,id)).thenReturn(java.util.List.of("PLACED","PLACED"));service.advance(id,OrderTracking.Status.PROCESSING);verify(jdbc).update("UPDATE seller_shipment SET status=? WHERE order_id=?","PROCESSING",id);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.advance(id,OrderTracking.Status.SHIPPED));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.advance(id,OrderTracking.Status.PLACED));}
@Test void invalidPageIsRejected(){var service=new OrderTracking(mock(JdbcTemplate.class),mock(UserRepository.class));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.all(-1));}

@Test void cancelRestoresStockReopensListingAndMarksOrderCancelled() throws Exception {
	var jdbc=mock(JdbcTemplate.class);
	var service=new OrderTracking(jdbc,usersWith(7L));
	UUID id=UUID.randomUUID();
	when(jdbc.queryForList(CANCEL_LOCK,id,7L)).thenReturn(List.<Map<String,Object>>of(Map.of("status","PLACED")));
	when(jdbc.queryForObject(STARTED_COUNT,Integer.class,id)).thenReturn(0);

	service.cancel(BUYER_EMAIL,id);

	verify(jdbc).update("UPDATE book b SET stock=b.stock+i.quantity FROM order_item i WHERE i.order_id=? AND i.book_id=b.id",id);
	verify(jdbc).update("UPDATE marketplace_resell_listings r SET status='AVAILABLE' FROM order_item i WHERE i.order_id=? AND i.listing_id=r.id AND r.status='CLOSED'",id);
	verify(jdbc).update("UPDATE seller_shipment SET status='CANCELLED' WHERE order_id=?",id);
	verify(jdbc).update("UPDATE purchase_order SET status='CANCELLED' WHERE id=?",id);
}

@Test void cancelRejectsOrderThatIsNoLongerPlaced() throws Exception {
	var jdbc=mock(JdbcTemplate.class);
	var service=new OrderTracking(jdbc,usersWith(7L));
	UUID id=UUID.randomUUID();
	when(jdbc.queryForList(CANCEL_LOCK,id,7L)).thenReturn(List.<Map<String,Object>>of(Map.of("status","PROCESSING")));
	when(jdbc.queryForObject(STARTED_COUNT,Integer.class,id)).thenReturn(0);

	var ex=assertThrows(ResponseStatusException.class,()->service.cancel(BUYER_EMAIL,id));
	assertEquals(HttpStatus.CONFLICT.value(),ex.getStatusCode().value());
	assertNoWrites(jdbc);
}

@Test void cancelRejectsAlreadyShippedOrderWithConflict() throws Exception {
	var jdbc=mock(JdbcTemplate.class);
	var service=new OrderTracking(jdbc,usersWith(7L));
	UUID id=UUID.randomUUID();
	when(jdbc.queryForList(CANCEL_LOCK,id,7L)).thenReturn(List.<Map<String,Object>>of(Map.of("status","SHIPPED")));
	when(jdbc.queryForObject(STARTED_COUNT,Integer.class,id)).thenReturn(1);

	var ex=assertThrows(ResponseStatusException.class,()->service.cancel(BUYER_EMAIL,id));
	assertEquals(HttpStatus.CONFLICT.value(),ex.getStatusCode().value());
	assertNoWrites(jdbc);
}

@Test void cancelRejectsOrderWhenAShipmentAlreadyStarted() throws Exception {
	var jdbc=mock(JdbcTemplate.class);
	var service=new OrderTracking(jdbc,usersWith(7L));
	UUID id=UUID.randomUUID();
	when(jdbc.queryForList(CANCEL_LOCK,id,7L)).thenReturn(List.<Map<String,Object>>of(Map.of("status","PLACED")));
	when(jdbc.queryForObject(STARTED_COUNT,Integer.class,id)).thenReturn(1);

	var ex=assertThrows(ResponseStatusException.class,()->service.cancel(BUYER_EMAIL,id));
	assertEquals(HttpStatus.CONFLICT.value(),ex.getStatusCode().value());
	assertNoWrites(jdbc);
}

@Test void cancelOfAnotherBuyersOrderIsNotFound() throws Exception {
	var jdbc=mock(JdbcTemplate.class);
	var service=new OrderTracking(jdbc,usersWith(7L));
	UUID id=UUID.randomUUID();
	// The lock query filters on buyer_id, so another buyer's order yields no row.
	when(jdbc.queryForList(CANCEL_LOCK,id,7L)).thenReturn(List.of());

	var ex=assertThrows(ResponseStatusException.class,()->service.cancel(BUYER_EMAIL,id));
	assertEquals(HttpStatus.NOT_FOUND.value(),ex.getStatusCode().value());
	assertNoWrites(jdbc);
}

private static void assertNoWrites(JdbcTemplate jdbc){
	assertTrue(mockingDetails(jdbc).getInvocations().stream().noneMatch(i->i.getMethod().getName().equals("update")));
}

private static UserRepository usersWith(long id) throws Exception {
	var user=new User("Buyer",BUYER_EMAIL,"hash");
	Field field=User.class.getDeclaredField("id");
	field.setAccessible(true);
	field.set(user,id);
	var users=mock(UserRepository.class);
	when(users.findByEmailIgnoreCase(BUYER_EMAIL)).thenReturn(Optional.of(user));
	return users;
}
}

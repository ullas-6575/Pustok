package com.example.EPustok.order;
import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class OrderTrackingTest {
private static final String BUYER_EMAIL="buyer@example.com";
private static final String CANCEL_LOCK="SELECT status FROM purchase_order WHERE id=? AND buyer_id=? FOR UPDATE";
private static final String STARTED_COUNT="SELECT COUNT(*) FROM seller_shipment WHERE order_id=? AND status<>'PLACED'";
private static final String ORDER_LOCK="SELECT id FROM purchase_order WHERE id=? FOR UPDATE";
private static final String PAID="SELECT COUNT(*) FROM payment_payments WHERE order_id=? AND (status='COMPLETED' OR gateway='COD')";
@Test void statusUpdateRequiresPreviousStage(){
var jdbc=mock(JdbcTemplate.class);var service=new OrderTracking(jdbc,mock(UserRepository.class));UUID id=UUID.randomUUID();
lockExists(jdbc,id);
paidOrder(jdbc,id,1);
when(jdbc.queryForList("SELECT status FROM seller_shipment WHERE order_id=?",String.class,id)).thenReturn(List.of("PLACED","PLACED"));
service.advance(id,OrderTracking.Status.PROCESSING);
verify(jdbc).update("UPDATE seller_shipment SET status=? WHERE order_id=?","PROCESSING",id);
assertThrows(ResponseStatusException.class,()->service.advance(id,OrderTracking.Status.SHIPPED));
assertThrows(ResponseStatusException.class,()->service.advance(id,OrderTracking.Status.PLACED));
}

@Test void adminCannotMoveUnpaidOrderToProcessing(){
var jdbc=mock(JdbcTemplate.class);var service=new OrderTracking(jdbc,mock(UserRepository.class));UUID id=UUID.randomUUID();
lockExists(jdbc,id);
paidOrder(jdbc,id,0);
var ex=assertThrows(ResponseStatusException.class,()->service.advance(id,OrderTracking.Status.PROCESSING));
assertEquals(HttpStatus.CONFLICT,ex.getStatusCode());
assertNoUpdates(jdbc);
}

@Test void adminAdvanceOfUnknownOrderIsNotFound(){
var jdbc=mock(JdbcTemplate.class);var service=new OrderTracking(jdbc,mock(UserRepository.class));UUID id=UUID.randomUUID();
when(jdbc.query(eq(ORDER_LOCK),any(RowMapper.class),eq(id))).thenReturn(List.of());
var ex=assertThrows(ResponseStatusException.class,()->service.advance(id,OrderTracking.Status.PROCESSING));
assertEquals(HttpStatus.NOT_FOUND,ex.getStatusCode());
assertNoUpdates(jdbc);
}

@Test void invalidPageIsRejected(){var service=new OrderTracking(mock(JdbcTemplate.class),mock(UserRepository.class));assertThrows(ResponseStatusException.class,()->service.all(-1));}

@Test void buyerOrderListCarriesLatestPaymentGateway() throws Exception {
	var jdbc=mock(JdbcTemplate.class);
	var service=new OrderTracking(jdbc,usersWith(7L));
	UUID id=UUID.randomUUID();
	when(jdbc.queryForList(contains("AS gateway"),eq(7L),eq(0)))
			.thenReturn(List.<Map<String,Object>>of(Map.of("id",id,"status","PLACED","gateway","BKASH")));

	var rows=service.mine(BUYER_EMAIL,0);

	assertEquals("BKASH",rows.get(0).get("gateway"));
	verify(jdbc).queryForList(contains("FROM payment_payments p WHERE p.order_id=o.id"),eq(7L),eq(0));
}

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
	verify(jdbc).update("UPDATE payment_payments SET status='FAILED' WHERE order_id=? AND status='PENDING'",id);
}

@Test void cancelOfAlreadyCancelledOrderIsConflictAndRestoresNothingTwice() throws Exception {
	var jdbc=mock(JdbcTemplate.class);
	var service=new OrderTracking(jdbc,usersWith(7L));
	UUID id=UUID.randomUUID();
	when(jdbc.queryForList(CANCEL_LOCK,id,7L)).thenReturn(List.<Map<String,Object>>of(Map.of("status","CANCELLED")));

	var ex=assertThrows(ResponseStatusException.class,()->service.cancel(BUYER_EMAIL,id));
	assertEquals(HttpStatus.CONFLICT.value(),ex.getStatusCode().value());
	assertEquals("This order is already cancelled.",ex.getReason());
	assertNoWrites(jdbc);
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

private static void lockExists(JdbcTemplate jdbc,UUID id){
	when(jdbc.query(eq(ORDER_LOCK),any(RowMapper.class),eq(id))).thenReturn(List.of(id));
}

private static void paidOrder(JdbcTemplate jdbc,UUID id,int count){
	when(jdbc.queryForObject(PAID,Integer.class,id)).thenReturn(count);
}

private static void assertNoWrites(JdbcTemplate jdbc){
	assertTrue(mockingDetails(jdbc).getInvocations().stream().noneMatch(i->i.getMethod().getName().equals("update")));
}

private static void assertNoUpdates(JdbcTemplate jdbc){
	assertNoWrites(jdbc);
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

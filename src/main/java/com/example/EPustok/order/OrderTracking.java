package com.example.EPustok.order;
import com.example.EPustok.identity.UserRepository;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service @Transactional(readOnly=true) public class OrderTracking {
private final JdbcTemplate jdbc;private final UserRepository users;
public OrderTracking(JdbcTemplate jdbc,UserRepository users){this.jdbc=jdbc;this.users=users;}
private int offset(int page){if(page<0||page>1000000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);return page*20;}
/** Each row carries "gateway": the gateway of the order's payment, or null when no payment row exists yet. */
@PreAuthorize("hasAnyRole('BUYER','SELLER')") public List<Map<String,Object>> mine(String email,int page){long id=users.findByEmailIgnoreCase(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).getId();return jdbc.queryForList("SELECT o.*,(SELECT p.gateway FROM payment_payments p WHERE p.order_id=o.id ORDER BY p.created_at DESC,p.id DESC LIMIT 1) AS gateway FROM purchase_order o WHERE o.buyer_id=? ORDER BY o.created_at DESC,o.id LIMIT 20 OFFSET ?",id,offset(page));}
/** Admin list. Each row carries "gateway", like mine(). */
@PreAuthorize("hasRole('ADMIN')") public List<Map<String,Object>> all(int page){return jdbc.queryForList("SELECT o.*,(SELECT p.gateway FROM payment_payments p WHERE p.order_id=o.id ORDER BY p.created_at DESC,p.id DESC LIMIT 1) AS gateway FROM purchase_order o ORDER BY o.created_at DESC,o.id LIMIT 20 OFFSET ?",offset(page));}
@PreAuthorize("hasRole('ADMIN')") public Map<String,Object> detail(UUID id){var rows=jdbc.queryForList("SELECT * FROM purchase_order WHERE id=?",id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);return rows.get(0);}
@PreAuthorize("hasRole('ADMIN')") public List<Map<String,Object>> items(UUID id){detail(id);return jdbc.queryForList("SELECT i.title,i.seller_id,i.unit_price,i.quantity,i.unit_price*i.quantity AS subtotal,s.status AS shipment_status FROM order_item i JOIN seller_shipment s ON s.order_id=i.order_id AND s.seller_id=i.seller_id WHERE i.order_id=? ORDER BY i.id",id);}
public enum Status { PLACED, PROCESSING, SHIPPED, DELIVERED }
/**
 * Buyer cancellation is allowed only before any seller starts processing. Stock and used-book listings
 * are restored in the same transaction. The order row lock serializes this with seller shipment updates.
 * A pending payment for the order is marked FAILED. A completed payment is not refunded here.
 */
@PreAuthorize("hasAnyRole('BUYER','SELLER')") @Transactional public void cancel(String email,UUID id){
long buyer=users.findByEmailIgnoreCase(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).getId();
var rows=jdbc.queryForList("SELECT status FROM purchase_order WHERE id=? AND buyer_id=? FOR UPDATE",id,buyer);
if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
String status=String.valueOf(rows.get(0).get("status"));
if("CANCELLED".equals(status))throw new ResponseStatusException(HttpStatus.CONFLICT,"This order is already cancelled.");
int started=jdbc.queryForObject("SELECT COUNT(*) FROM seller_shipment WHERE order_id=? AND status<>'PLACED'",Integer.class,id);
if(!"PLACED".equals(status)||started>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"This order can no longer be cancelled because a seller has started processing it.");
jdbc.update("UPDATE book b SET stock=b.stock+i.quantity FROM order_item i WHERE i.order_id=? AND i.book_id=b.id",id);
jdbc.update("UPDATE marketplace_resell_listings r SET status='AVAILABLE' FROM order_item i WHERE i.order_id=? AND i.listing_id=r.id AND r.status='CLOSED'",id);
jdbc.update("UPDATE seller_shipment SET status='CANCELLED' WHERE order_id=?",id);
jdbc.update("UPDATE purchase_order SET status='CANCELLED' WHERE id=?",id);
jdbc.update("UPDATE payment_payments SET status='FAILED' WHERE order_id=? AND status='PENDING'",id);
}
@PreAuthorize("hasRole('ADMIN')") @Transactional public void advance(UUID id,Status next){
if(next==Status.PLACED)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose the next status");
ShipmentService.lockOrder(jdbc,id);
if(next==Status.PROCESSING||next==Status.SHIPPED)ShipmentService.requirePaid(jdbc,id);
String previous=Status.values()[next.ordinal()-1].name();
var states=jdbc.queryForList("SELECT status FROM seller_shipment WHERE order_id=?",String.class,id);
if(states.isEmpty()||states.stream().anyMatch(state -> !state.equals(previous)))throw new ResponseStatusException(HttpStatus.CONFLICT,"Shipments have different statuses. Update each shipment separately.");
jdbc.update("UPDATE seller_shipment SET status=? WHERE order_id=?",next.name(),id);
ShipmentService.recompute(jdbc,id);
}
}

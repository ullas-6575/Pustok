package com.example.EPustok.order;
import com.example.EPustok.identity.UserRepository;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service @Transactional(readOnly=true) public class ShipmentService {
private final JdbcTemplate jdbc;private final UserRepository users;
public ShipmentService(JdbcTemplate jdbc,UserRepository users){this.jdbc=jdbc;this.users=users;}
private long seller(String email){return users.findByEmailIgnoreCase(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).getId();}
/** Cancelled shipments are not listed for sellers. */
@PreAuthorize("hasRole('SELLER')") public List<Map<String,Object>> mine(String email,int page){if(page<0||page>1000000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);return jdbc.queryForList("SELECT o.id,o.recipient_name,o.phone,o.address,o.created_at,s.status FROM seller_shipment s JOIN purchase_order o ON o.id=s.order_id WHERE s.seller_id=? AND s.status<>'CANCELLED' ORDER BY o.created_at DESC,o.id LIMIT 20 OFFSET ?",seller(email),page*20);}
@PreAuthorize("hasRole('SELLER')") public List<Map<String,Object>> items(String email,UUID id){long seller=seller(email);return jdbc.queryForList("SELECT title,quantity,unit_price FROM order_item WHERE order_id=? AND seller_id=? ORDER BY id",id,seller);}
@PreAuthorize("hasRole('SELLER')") @Transactional public void advance(String email,UUID id,OrderTracking.Status next){if(next!=OrderTracking.Status.PROCESSING&&next!=OrderTracking.Status.SHIPPED)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Sellers can mark processing or shipped");update(id,seller(email),next);}
@PreAuthorize("hasRole('ADMIN')") @Transactional public void adminAdvance(UUID id,long seller,OrderTracking.Status next){update(id,seller,next);}
private void update(UUID id,long seller,OrderTracking.Status next){
if(next==OrderTracking.Status.PLACED)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
lockOrder(jdbc,id);
if(next==OrderTracking.Status.PROCESSING||next==OrderTracking.Status.SHIPPED)requirePaid(jdbc,id);
String previous=OrderTracking.Status.values()[next.ordinal()-1].name();
if(jdbc.update("UPDATE seller_shipment SET status=? WHERE order_id=? AND seller_id=? AND status=?",next.name(),id,seller,previous)!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Shipment changed or transition is invalid");
recompute(jdbc,id);
}
/** Locks the order row with FOR UPDATE. An unknown order id is a 404. */
static void lockOrder(JdbcTemplate jdbc,UUID id){
var rows=jdbc.query("SELECT id FROM purchase_order WHERE id=? FOR UPDATE",(r,n)->r.getObject(1,UUID.class),id);
if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found.");
}
/** Shipments can move to PROCESSING or SHIPPED only when the order is paid: a COMPLETED payment or cash on delivery. */
static void requirePaid(JdbcTemplate jdbc,UUID id){
Integer paid=jdbc.queryForObject("SELECT COUNT(*) FROM payment_payments WHERE order_id=? AND (status='COMPLETED' OR gateway='COD')",Integer.class,id);
if(paid==null||paid==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"The order has not been paid yet");
}
/**
 * Order status is the lowest shipment status. A paid order (COMPLETED payment) never drops back to PLACED
 * once a seller has started processing, so a PLACED minimum maps to PROCESSING for it.
 */
static void recompute(JdbcTemplate jdbc,UUID id){jdbc.update("UPDATE purchase_order o SET status=(SELECT CASE MIN(CASE s.status WHEN 'PLACED' THEN 0 WHEN 'PROCESSING' THEN 1 WHEN 'SHIPPED' THEN 2 ELSE 3 END) WHEN 0 THEN CASE WHEN EXISTS(SELECT 1 FROM payment_payments p WHERE p.order_id=o.id AND p.status='COMPLETED') THEN 'PROCESSING' ELSE 'PLACED' END WHEN 1 THEN 'PROCESSING' WHEN 2 THEN 'SHIPPED' ELSE 'DELIVERED' END FROM seller_shipment s WHERE s.order_id=o.id) WHERE o.id=?",id);}
}

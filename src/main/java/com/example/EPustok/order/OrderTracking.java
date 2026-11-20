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
@PreAuthorize("hasAnyRole('BUYER','SELLER')") public List<Map<String,Object>> mine(String email,int page){long id=users.findByEmailIgnoreCase(email).orElseThrow().getId();return jdbc.queryForList("SELECT * FROM purchase_order WHERE buyer_id=? ORDER BY created_at DESC,id LIMIT 20 OFFSET ?",id,offset(page));}
@PreAuthorize("hasRole('ADMIN')") public List<Map<String,Object>> all(int page){return jdbc.queryForList("SELECT * FROM purchase_order ORDER BY created_at DESC,id LIMIT 20 OFFSET ?",offset(page));}
@PreAuthorize("hasRole('ADMIN')") public Map<String,Object> detail(UUID id){var rows=jdbc.queryForList("SELECT * FROM purchase_order WHERE id=?",id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);return rows.get(0);}
@PreAuthorize("hasRole('ADMIN')") public List<Map<String,Object>> items(UUID id){detail(id);return jdbc.queryForList("SELECT title,unit_price,quantity,unit_price*quantity AS subtotal FROM order_item WHERE order_id=? ORDER BY id",id);}
public enum Status { PLACED, PROCESSING, SHIPPED, DELIVERED }
@PreAuthorize("hasRole('ADMIN')") @Transactional public void advance(UUID id,Status next){
if(next==Status.PLACED)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose the next status");
String previous=Status.values()[next.ordinal()-1].name();
if(jdbc.update("UPDATE purchase_order SET status=? WHERE id=? AND status=?",next.name(),id,previous)!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Order status changed or transition is invalid. Refresh the order.");
}
}

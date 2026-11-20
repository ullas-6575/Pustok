package com.example.EPustok.order;
import com.example.EPustok.identity.UserRepository;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service @Transactional @PreAuthorize("hasAnyRole('BUYER','SELLER')")
public class CheckoutService {
private final JdbcTemplate jdbc; private final UserRepository users;
public CheckoutService(JdbcTemplate jdbc,UserRepository users){this.jdbc=jdbc;this.users=users;}
private long uid(String email){return users.findByEmailIgnoreCase(email).orElseThrow().getId();}
public UUID place(String email,String name,String phone,String address,UUID requestId){
long buyer=uid(email);jdbc.queryForObject("SELECT id FROM identity_users WHERE id=? FOR UPDATE",Long.class,buyer);
var previous=jdbc.query("SELECT id FROM purchase_order WHERE buyer_id=? AND request_id=?",(r,n)->r.getObject(1,UUID.class),buyer,requestId);if(!previous.isEmpty())return previous.get(0);
var lines=jdbc.query("SELECT b.id,b.seller_id,b.title,b.price,b.stock,c.quantity FROM cart_item c JOIN book b ON b.id=c.book_id WHERE c.user_id=? ORDER BY b.id FOR UPDATE OF b,c",(r,n)->new Line(r.getLong(1),r.getLong(2),r.getString(3),r.getBigDecimal(4),r.getInt(5),r.getInt(6)),buyer);
if(lines.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Your cart is empty.");
BigDecimal total=BigDecimal.ZERO;
for(var l:lines){if(l.seller()==buyer)throw new ResponseStatusException(HttpStatus.CONFLICT,"Your cart contains your own book. Remove it before ordering.");if(l.quantity()<1||l.quantity()>l.stock())throw new ResponseStatusException(HttpStatus.CONFLICT,"Stock changed for " + l.title() + ". Update your cart.");total=total.add(l.price().multiply(BigDecimal.valueOf(l.quantity())));}
UUID id=UUID.randomUUID();jdbc.update("INSERT INTO purchase_order(id,buyer_id,recipient_name,phone,address,total,request_id) VALUES (?,?,?,?,?,?,?)",id,buyer,name.trim(),phone.trim(),address.trim(),total,requestId);
for(var l:lines){jdbc.update("INSERT INTO order_item(order_id,book_id,seller_id,title,unit_price,quantity) VALUES (?,?,?,?,?,?)",id,l.book(),l.seller(),l.title(),l.price(),l.quantity());int changed=jdbc.update("UPDATE book SET stock=stock-? WHERE id=? AND stock>=?",l.quantity(),l.book(),l.quantity());if(changed!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Stock changed. Try again.");}
jdbc.update("DELETE FROM cart_item WHERE user_id=?",buyer);return id;
}
@Transactional(readOnly=true) public Map<String,Object> order(String email,UUID id){var rows=jdbc.queryForList("SELECT * FROM purchase_order WHERE id=? AND buyer_id=?",id,uid(email));if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);return rows.get(0);}
@Transactional(readOnly=true) public List<Map<String,Object>> items(String email,UUID id){order(email,id);return jdbc.queryForList("SELECT title,unit_price,quantity,unit_price*quantity AS subtotal FROM order_item WHERE order_id=? ORDER BY id",id);}
private record Line(long book,long seller,String title,BigDecimal price,int stock,int quantity){}
}

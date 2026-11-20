package com.example.EPustok.cart;
import com.example.EPustok.identity.*;
import com.example.EPustok.market.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service @Transactional @PreAuthorize("hasAnyRole('BUYER','SELLER')")
public class CartService {
 private final JdbcTemplate jdbc; private final UserRepository users; private final Books books;
 public CartService(JdbcTemplate jdbc,UserRepository users,Books books){this.jdbc=jdbc;this.users=users;this.books=books;}
 private long user(String email){return users.findByEmailIgnoreCase(email).orElseThrow().getId();}
 private void lock(long id){jdbc.queryForObject("SELECT id FROM identity_users WHERE id=? FOR UPDATE",Long.class,id);}
 private Book available(long bookId,long user,int quantity){
 if(quantity<1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quantity must be positive");
 Book b=books.findById(bookId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Book no longer available"));
 if(b.getSellerId().equals(user))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"You cannot buy your own book");
 if(quantity>b.getStock())throw new ResponseStatusException(HttpStatus.CONFLICT,"Requested quantity exceeds available stock");return b;
 }
 public void add(String email,long bookId,int quantity){long id=user(email);lock(id);if(quantity<1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quantity must be positive");
 Integer current=jdbc.queryForObject("SELECT COALESCE(SUM(quantity),0)::integer FROM cart_item WHERE user_id=? AND book_id=?",Integer.class,id,bookId);
 long total=(long)current+quantity;if(total>Integer.MAX_VALUE)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quantity too large");available(bookId,id,(int)total);
 jdbc.update("INSERT INTO cart_item(user_id,book_id,quantity) VALUES (?,?,?) ON CONFLICT(user_id,book_id) DO UPDATE SET quantity=EXCLUDED.quantity",id,bookId,(int)total);
 }
 public void update(String email,long bookId,int quantity){long id=user(email);lock(id);available(bookId,id,quantity);if(jdbc.update("UPDATE cart_item SET quantity=? WHERE user_id=? AND book_id=?",quantity,id,bookId)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Cart item not found");}
 public void remove(String email,long bookId){jdbc.update("DELETE FROM cart_item WHERE user_id=? AND book_id=?",user(email),bookId);}
 @Transactional(readOnly=true) public List<Line> items(String email){return jdbc.query("SELECT b.id,b.title,b.author,b.price,b.stock,c.quantity FROM cart_item c JOIN book b ON b.id=c.book_id WHERE c.user_id=? ORDER BY b.id",(rs,row)->new Line(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getBigDecimal(4),rs.getInt(5),rs.getInt(6)),user(email));}
 public record Line(long bookId,String title,String author,BigDecimal price,int stock,int quantity){public BigDecimal getSubtotal(){return price.multiply(BigDecimal.valueOf(quantity));}}
}

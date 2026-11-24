package com.example.EPustok.order;
import com.example.EPustok.identity.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;
import java.util.UUID;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test") @Transactional @WithMockUser(roles="BUYER")
class CheckoutServiceIT {
@Autowired CheckoutService checkout; @Autowired JdbcTemplate jdbc; @Autowired UserRepository users;
@Test void orderDecrementsStockClearsCartAndDeduplicates(){
String email="checkout-"+UUID.randomUUID()+"@example.com";
var buyer=users.saveAndFlush(new User("Checkout test",email,"test-only"));
var seller=users.saveAndFlush(new User("Seller test",UUID.randomUUID()+"@example.com","test-only"));
jdbc.update("INSERT INTO seller_profile(id,shop_name,phone,address,status) VALUES (?,?,?,?,?)",seller.getId(),"Test shop","01234567890","Dhaka","APPROVED");
Long category=jdbc.queryForObject("INSERT INTO category(name) VALUES (?) RETURNING id",Long.class,"Test-"+UUID.randomUUID());
Long book=jdbc.queryForObject("INSERT INTO book(seller_id,title,author,price,stock,category_id) VALUES (?,?,?,?,?,?) RETURNING id",Long.class,seller.getId(),"Checkout book","Author",new BigDecimal("125.50"),5,category);
jdbc.update("INSERT INTO cart_item(user_id,book_id,quantity) VALUES (?,?,?)",buyer.getId(),book,2);
UUID request=UUID.randomUUID();UUID id=checkout.place(email,"Recipient","01234567890","Dhaka",request);
assertEquals(3,jdbc.queryForObject("SELECT stock FROM book WHERE id=?",Integer.class,book));
assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM cart_item WHERE user_id=?",Integer.class,buyer.getId()));
assertEquals(new BigDecimal("251.00"),checkout.order(email,id).get("total"));
assertEquals(id,checkout.place(email,"Recipient","01234567890","Dhaka",request));
assertTrue(checkout.place(email,"Recipient","01234567890","Dhaka",request,null).replayed());
assertEquals(3,jdbc.queryForObject("SELECT stock FROM book WHERE id=?",Integer.class,book));
assertThrows(org.springframework.web.server.ResponseStatusException.class,()->checkout.order(seller.getEmail(),id));
}
}

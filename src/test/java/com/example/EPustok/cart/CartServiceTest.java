package com.example.EPustok.cart;
import com.example.EPustok.identity.*;
import com.example.EPustok.market.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.math.BigDecimal;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class CartServiceTest {
private User user() throws Exception {var u=new User("Buyer","b@example.com","hash");var f=User.class.getDeclaredField("id");f.setAccessible(true);f.set(u,1L);return u;}
@Test void blocksOwnBooksAndStockOverflow() throws Exception {
var jdbc=mock(JdbcTemplate.class);var users=mock(UserRepository.class);var books=mock(Books.class);when(users.findByEmailIgnoreCase("b@example.com")).thenReturn(Optional.of(user()));
var b=new Book(1L);b.update("Title","Author",BigDecimal.TEN,2,new Category("Fiction"));when(books.findById(3L)).thenReturn(Optional.of(b));
var service=new CartService(jdbc,users,books);
assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.update("b@example.com",3,1));
b=new Book(2L);b.update("Title","Author",BigDecimal.TEN,2,new Category("Fiction"));when(books.findById(3L)).thenReturn(Optional.of(b));
assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.update("b@example.com",3,3));
assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.update("b@example.com",3,0));
}
@Test void removalIsScopedToCurrentUser() throws Exception {
var jdbc=mock(JdbcTemplate.class);var users=mock(UserRepository.class);when(users.findByEmailIgnoreCase("b@example.com")).thenReturn(Optional.of(user()));
new CartService(jdbc,users,mock(Books.class)).remove("b@example.com",5);
verify(jdbc).update("DELETE FROM cart_item WHERE user_id=? AND book_id=?",1L,5L);
}
@Test void subtotalUsesDecimalArithmetic(){assertEquals(new BigDecimal("30.75"),new CartService.Line(1,"Book","Author",new BigDecimal("10.25"),5,3).getSubtotal());}
}

package com.example.EPustok.cart;
import com.example.EPustok.identity.*;
import com.example.EPustok.market.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
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
@Test void unknownUserIsUnauthorizedInsteadOfServerError(){
var ex=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->new CartService(mock(JdbcTemplate.class),mock(UserRepository.class),mock(Books.class)).remove("ghost@example.com",5));
assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED,ex.getStatusCode());
}
@Test void subtotalUsesDecimalArithmetic(){assertEquals(new BigDecimal("30.75"),new CartService.Line(1,"Book","Author",new BigDecimal("10.25"),5,3).getSubtotal());}
@Test void blocksOwnResellListingInCart() throws Exception {
var jdbc=mock(JdbcTemplate.class);var users=mock(UserRepository.class);when(users.findByEmailIgnoreCase("b@example.com")).thenReturn(Optional.of(user()));
when(jdbc.queryForList(anyString(), eq(7L))).thenReturn(java.util.List.of(java.util.Map.of("user_id", 1L, "listing_type", "RESELL", "status", "AVAILABLE", "price", new BigDecimal("150.00"))));
assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> new CartService(jdbc, users, mock(Books.class)).addResell("b@example.com", 7L));
}

@Test void updateIsScopedToCallersCartItem() throws Exception {
var jdbc=mock(JdbcTemplate.class);var users=mock(UserRepository.class);var books=mock(Books.class);when(users.findByEmailIgnoreCase("b@example.com")).thenReturn(Optional.of(user()));
var b=new Book(2L);b.update("Title","Author",BigDecimal.TEN,5,new Category("Fiction"));when(books.findById(3L)).thenReturn(Optional.of(b));
// Another buyer's cart row is invisible to this user: the WHERE clause matches zero rows for user 1.
when(jdbc.update("UPDATE cart_item SET quantity=? WHERE user_id=? AND book_id=?",2,1L,3L)).thenReturn(0);
var ex=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->new CartService(jdbc,users,books).update("b@example.com",3,2));
assertEquals(org.springframework.http.HttpStatus.NOT_FOUND,ex.getStatusCode());
verify(jdbc).update("UPDATE cart_item SET quantity=? WHERE user_id=? AND book_id=?",2,1L,3L);
}
@Test void updateWithinStockPersistsQuantity() throws Exception {
var jdbc=mock(JdbcTemplate.class);var users=mock(UserRepository.class);var books=mock(Books.class);when(users.findByEmailIgnoreCase("b@example.com")).thenReturn(Optional.of(user()));
var b=new Book(2L);b.update("Title","Author",BigDecimal.TEN,5,new Category("Fiction"));when(books.findById(3L)).thenReturn(Optional.of(b));
when(jdbc.update("UPDATE cart_item SET quantity=? WHERE user_id=? AND book_id=?",5,1L,3L)).thenReturn(1);
new CartService(jdbc,users,books).update("b@example.com",3,5);
verify(jdbc).update("UPDATE cart_item SET quantity=? WHERE user_id=? AND book_id=?",5,1L,3L);
}
@Test void addRejectsCumulativeQuantityAboveStock() throws Exception {
var jdbc=mock(JdbcTemplate.class);var users=mock(UserRepository.class);var books=mock(Books.class);when(users.findByEmailIgnoreCase("b@example.com")).thenReturn(Optional.of(user()));
var b=new Book(2L);b.update("Title","Author",BigDecimal.TEN,2,new Category("Fiction"));when(books.findById(3L)).thenReturn(Optional.of(b));
when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(1L),eq(3L))).thenReturn(1);
var ex=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->new CartService(jdbc,users,books).add("b@example.com",3,2));
assertEquals(org.springframework.http.HttpStatus.CONFLICT,ex.getStatusCode());
verify(jdbc,never()).update(startsWith("INSERT INTO cart_item"),any(),any(),any());
}
}

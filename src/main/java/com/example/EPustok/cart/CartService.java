package com.example.EPustok.cart;

import com.example.EPustok.identity.UserRepository;
import com.example.EPustok.market.Book;
import com.example.EPustok.market.Books;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
@PreAuthorize("hasAnyRole('BUYER','SELLER')")
public class CartService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final Books books;

    public CartService(JdbcTemplate jdbc, UserRepository users, Books books) {
        this.jdbc = jdbc;
        this.users = users;
        this.books = books;
    }

    private long user(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED))
                .getId();
    }

    private void lock(long id) {
        jdbc.queryForObject("SELECT id FROM identity_users WHERE id=? FOR UPDATE", Long.class, id);
    }

    private Book available(long bookId, long user, int quantity) {
        if (quantity < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive");
        }
        Book b = books.findById(bookId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book no longer available"));
        if (b.getSellerId().equals(user)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot buy your own book");
        }
        if (quantity > b.getStock()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Requested quantity exceeds available stock");
        }
        return b;
    }

    public void add(String email, long bookId, int quantity) {
        long id = user(email);
        lock(id);
        if (quantity < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive");
        }
        Integer current = jdbc.queryForObject(
                "SELECT COALESCE(SUM(quantity),0)::integer FROM cart_item WHERE user_id=? AND book_id=?",
                Integer.class, id, bookId);
        long total = (long) current + quantity;
        if (total > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity too large");
        }
        available(bookId, id, (int) total);
        jdbc.update(
                "INSERT INTO cart_item(user_id,book_id,quantity) VALUES (?,?,?) ON CONFLICT(user_id,book_id) DO UPDATE SET quantity=EXCLUDED.quantity",
                id, bookId, (int) total);
    }

    public void update(String email, long bookId, int quantity) {
        long id = user(email);
        lock(id);
        available(bookId, id, quantity);
        if (jdbc.update("UPDATE cart_item SET quantity=? WHERE user_id=? AND book_id=?", quantity, id, bookId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found");
        }
    }

    public void remove(String email, long bookId) {
        long id = user(email);
        lock(id);
        jdbc.update("DELETE FROM cart_item WHERE user_id=? AND book_id=?", id, bookId);
    }

    public void addResell(String email, long listingId) {
        long id = user(email);
        lock(id);
        var rows = jdbc.queryForList(
                "SELECT user_id, listing_type, status, price FROM marketplace_resell_listings WHERE id=?",
                listingId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resell listing no longer available");
        }
        var row = rows.get(0);
        long ownerId = ((Number) row.get("user_id")).longValue();
        String type = String.valueOf(row.get("listing_type"));
        String status = String.valueOf(row.get("status"));
        if (!"RESELL".equals(type) || row.get("price") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only resell listings can be added to cart");
        }
        if (!"AVAILABLE".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This used book is no longer available");
        }
        if (ownerId == id) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot buy your own listing");
        }
        jdbc.update(
                "INSERT INTO resell_cart_item(user_id, listing_id) VALUES (?, ?) ON CONFLICT(user_id, listing_id) DO NOTHING",
                id, listingId);
    }

    public void removeResell(String email, long listingId) {
        long id = user(email);
        lock(id);
        jdbc.update("DELETE FROM resell_cart_item WHERE user_id=? AND listing_id=?", id, listingId);
    }

    @Transactional(readOnly = true)
    public List<Line> items(String email) {
        return jdbc.query(
                "SELECT b.id,b.title,b.author,b.price,b.stock,c.quantity FROM cart_item c JOIN book b ON b.id=c.book_id WHERE c.user_id=? ORDER BY b.id",
                (rs, row) -> new Line(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getInt(5), rs.getInt(6)),
                user(email));
    }

    @Transactional(readOnly = true)
    public List<ResellLine> resellItems(String email) {
        return jdbc.query(
                "SELECT r.id, r.title, r.author, r.book_condition, r.price, r.status FROM resell_cart_item c JOIN marketplace_resell_listings r ON r.id=c.listing_id WHERE c.user_id=? ORDER BY r.id",
                (rs, row) -> new ResellLine(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getString(6)),
                user(email));
    }

    @Transactional(readOnly = true)
    public int count(String email) {
        long id = user(email);
        Integer booksCount = jdbc.queryForObject(
                "SELECT COALESCE(SUM(quantity),0)::integer FROM cart_item WHERE user_id=?", Integer.class, id);
        Integer resellCount = jdbc.queryForObject(
                "SELECT COUNT(*)::integer FROM resell_cart_item WHERE user_id=?", Integer.class, id);
        return (booksCount == null ? 0 : booksCount) + (resellCount == null ? 0 : resellCount);
    }

    public record Line(long bookId, String title, String author, BigDecimal price, int stock, int quantity) {
        public BigDecimal getSubtotal() {
            return price.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public record ResellLine(long listingId, String title, String author, String bookCondition, BigDecimal price, String status) {}
}

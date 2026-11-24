package com.example.EPustok.order;

import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import com.example.EPustok.support.PostgresIT;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Twenty buyers check out one unit each of a book with five units in stock, all released at once.
 * On real PostgreSQL exactly five must succeed, the rest must get 409, and stock must end at exactly 0.
 * Runs under failsafe (./mvnw -B verify) and needs Docker for the Testcontainers PostgreSQL.
 */
class CheckoutConcurrencyIT extends PostgresIT {
	private static final int BUYERS = 20;
	private static final int STOCK = 5;

	@Autowired CheckoutService checkout;
	@Autowired JdbcTemplate jdbc;
	@Autowired UserRepository users;

	/** orderId is set when the checkout succeeded; status is the HTTP status of the outcome. */
	private record Attempt(UUID orderId, HttpStatusCode status) {}

	@Test void parallelCheckoutsNeverOversell() throws Exception {
		String run = UUID.randomUUID().toString();
		var seller = users.saveAndFlush(new User("Seller test", "seller-" + run + "@example.com", "test-only"));
		jdbc.update("INSERT INTO seller_profile(id,shop_name,phone,address,status) VALUES (?,?,?,?,?)",
				seller.getId(), "Test shop", "01234567890", "Dhaka", "APPROVED");
		Long category = jdbc.queryForObject("INSERT INTO category(name) VALUES (?) RETURNING id", Long.class, "Test-" + run);
		Long book = jdbc.queryForObject(
				"INSERT INTO book(seller_id,title,author,price,stock,category_id) VALUES (?,?,?,?,?,?) RETURNING id",
				Long.class, seller.getId(), "Scarce book", "Author", new BigDecimal("10.00"), STOCK, category);

		List<String> buyers = new ArrayList<>();
		for (int i = 0; i < BUYERS; i++) {
			String email = "buyer-" + run + "-" + i + "@example.com";
			var buyer = users.saveAndFlush(new User("Buyer test", email, "test-only"));
			jdbc.update("INSERT INTO cart_item(user_id,book_id,quantity) VALUES (?,?,?)", buyer.getId(), book, 1);
			buyers.add(email);
		}

		// Test data is committed (no @Transactional on this class), so each checkout runs in its own real transaction.
		CountDownLatch ready = new CountDownLatch(BUYERS);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(BUYERS);
		try {
			List<Future<Attempt>> futures = new ArrayList<>();
			for (String email : buyers) {
				futures.add(pool.submit(() -> attempt(email, ready, go)));
			}
			assertTrue(ready.await(30, TimeUnit.SECONDS), "buyer threads did not all start");
			go.countDown();

			List<Attempt> results = new ArrayList<>();
			for (Future<Attempt> future : futures) {
				results.add(future.get(60, TimeUnit.SECONDS));
			}

			long won = results.stream().filter(a -> a.orderId() != null).count();
			long rejected = results.stream().filter(a -> a.status().value() == HttpStatus.CONFLICT.value()).count();
			assertEquals(STOCK, won, "exactly STOCK checkouts must succeed");
			assertEquals(BUYERS - STOCK, rejected, "every other checkout must be rejected with 409");

			int stock = jdbc.queryForObject("SELECT stock FROM book WHERE id=?", Integer.class, book);
			assertEquals(0, stock, "stock must end at exactly zero, never negative");
			long orders = jdbc.queryForObject(
					"SELECT count(DISTINCT order_id) FROM order_item WHERE book_id=?", Long.class, book);
			assertEquals(won, orders, "one order per successful checkout");
			long unitsSold = jdbc.queryForObject(
					"SELECT COALESCE(SUM(quantity),0) FROM order_item WHERE book_id=?", Long.class, book);
			assertEquals(STOCK, unitsSold, "units ordered must equal the original stock");
			// Rejected buyers keep their cart line because their transactions rolled back.
			long cartLinesLeft = jdbc.queryForObject(
					"SELECT count(*) FROM cart_item WHERE book_id=?", Long.class, book);
			assertEquals(BUYERS - won, cartLinesLeft);
		} finally {
			pool.shutdownNow();
		}
	}

	private Attempt attempt(String email, CountDownLatch ready, CountDownLatch go) throws InterruptedException {
		ready.countDown();
		go.await();
		// The service is @PreAuthorize("hasAnyRole('BUYER','SELLER')"), and the security context is per thread.
		var context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new UsernamePasswordAuthenticationToken(
				email, "n/a", List.of(new SimpleGrantedAuthority("ROLE_BUYER"))));
		SecurityContextHolder.setContext(context);
		try {
			return new Attempt(checkout.place(email, "Recipient", "01234567890", "Dhaka", UUID.randomUUID()), HttpStatus.OK);
		} catch (ResponseStatusException e) {
			if (e.getStatusCode().value() != HttpStatus.CONFLICT.value()) {
				throw e;
			}
			return new Attempt(null, e.getStatusCode());
		} finally {
			SecurityContextHolder.clearContext();
		}
	}
}

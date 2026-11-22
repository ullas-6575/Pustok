package com.example.EPustok.order;

import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

class CheckoutServiceTest {

	private static final String EMAIL = "b@example.com";

	@Test void replayOfKnownRequestReturnsExistingOrderWithoutTouchingStock() throws Exception {
		UUID existing = UUID.randomUUID();
		var jdbc = jdbcWith(Map.of("FROM purchase_order", List.of(uuidRow(existing))));
		var service = new CheckoutService(jdbc, usersWith(7L));

		assertEquals(existing, service.place(EMAIL, "Recipient", "01234567890", "Dhaka", UUID.randomUUID()));
		assertNoWrites(jdbc);
	}

	@Test void emptyCartIsRejectedWithConflict() throws Exception {
		var jdbc = jdbcWith(Map.of());
		var service = new CheckoutService(jdbc, usersWith(7L));

		var ex = assertThrows(ResponseStatusException.class,
				() -> service.place(EMAIL, "Recipient", "01234567890", "Dhaka", UUID.randomUUID()));
		assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
		assertEquals("Your cart is empty.", ex.getReason());
		assertNoWrites(jdbc);
	}

	@Test void ownBookInCartIsRejected() throws Exception {
		// Book 11 is sold by user 7, the buyer.
		var jdbc = jdbcWith(Map.of("FROM cart_item", List.of(bookRow(11L, 7L, "Own book", "10.00", 5, 1))));
		var service = new CheckoutService(jdbc, usersWith(7L));

		var ex = assertThrows(ResponseStatusException.class,
				() -> service.place(EMAIL, "Recipient", "01234567890", "Dhaka", UUID.randomUUID()));
		assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
		assertTrue(ex.getReason().contains("own book"));
		assertNoWrites(jdbc);
	}

	@Test void quantityAboveStockIsRejectedBeforeAnyWrite() throws Exception {
		var jdbc = jdbcWith(Map.of("FROM cart_item", List.of(bookRow(11L, 8L, "Short book", "10.00", 1, 2))));
		var service = new CheckoutService(jdbc, usersWith(7L));

		var ex = assertThrows(ResponseStatusException.class,
				() -> service.place(EMAIL, "Recipient", "01234567890", "Dhaka", UUID.randomUUID()));
		assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
		assertEquals("Stock changed for Short book. Update your cart.", ex.getReason());
		assertNoWrites(jdbc);
	}

	/**
	 * Answers row queries by matching a fragment of their SQL and mapping the fake result rows.
	 * Other calls get default answers, so writes stay visible as recorded invocations.
	 */
	@SuppressWarnings("unchecked")
	private static JdbcTemplate jdbcWith(Map<String, List<ResultSet>> rowsBySqlFragment) {
		return mock(JdbcTemplate.class, (Answer<Object>) invocation -> {
			Object[] args = invocation.getArguments();
			boolean rowQuery = "query".equals(invocation.getMethod().getName())
					&& args.length > 1 && args[1] instanceof RowMapper;
			if (!rowQuery) {
				return Answers.RETURNS_DEFAULTS.answer(invocation);
			}
			String sql = (String) args[0];
			RowMapper<Object> mapper = (RowMapper<Object>) args[1];
			for (var entry : rowsBySqlFragment.entrySet()) {
				if (sql.contains(entry.getKey())) {
					List<Object> mapped = new ArrayList<>();
					for (ResultSet rs : entry.getValue()) {
						mapped.add(mapper.mapRow(rs, 1));
					}
					return mapped;
				}
			}
			return List.of();
		});
	}

	@Test void successfulCheckoutTotalsFromDatabasePricesAndSnapshotsThem() throws Exception {
		UUID request = UUID.randomUUID();
		var jdbc = jdbcWithUpdateResult(Map.of("FROM cart_item", List.of(bookRow(11L, 8L, "Book", "125.50", 5, 2))), 1);
		var service = new CheckoutService(jdbc, usersWith(7L));

		UUID id = service.place(EMAIL, " Recipient ", "01234567890", "Dhaka", request);

		assertNotNull(id);
		// Total is computed from the database price (125.50 x 2), not from anything the client sent.
		verify(jdbc).update(startsWith("INSERT INTO purchase_order"), any(), eq(7L), eq("Recipient"), eq("01234567890"),
				eq("Dhaka"), eq(new BigDecimal("251.00")), eq(request));
		// The order line keeps an immutable price snapshot.
		verify(jdbc).update(startsWith("INSERT INTO order_item"), any(), eq(11L), eq(8L), eq("Book"),
				eq(new BigDecimal("125.50")), eq(2));
		verify(jdbc).update("UPDATE book SET stock=stock-? WHERE id=? AND stock>=?", 2, 11L, 2);
		verify(jdbc).update("DELETE FROM cart_item WHERE user_id=?", 7L);
	}

	/** Like jdbcWith, but every UPDATE or INSERT reports the given changed-row count. */
	@SuppressWarnings("unchecked")
	private static JdbcTemplate jdbcWithUpdateResult(Map<String, List<ResultSet>> rowsBySqlFragment, int updateResult) {
		return mock(JdbcTemplate.class, (Answer<Object>) invocation -> {
			Object[] args = invocation.getArguments();
			if ("update".equals(invocation.getMethod().getName())) {
				return updateResult;
			}
			boolean rowQuery = "query".equals(invocation.getMethod().getName())
					&& args.length > 1 && args[1] instanceof RowMapper;
			if (!rowQuery) {
				return Answers.RETURNS_DEFAULTS.answer(invocation);
			}
			String sql = (String) args[0];
			RowMapper<Object> mapper = (RowMapper<Object>) args[1];
			for (var entry : rowsBySqlFragment.entrySet()) {
				if (sql.contains(entry.getKey())) {
					List<Object> mapped = new ArrayList<>();
					for (ResultSet rs : entry.getValue()) {
						mapped.add(mapper.mapRow(rs, 1));
					}
					return mapped;
				}
			}
			return List.of();
		});
	}

	private static void assertNoWrites(JdbcTemplate jdbc) {
		assertTrue(mockingDetails(jdbc).getInvocations().stream().noneMatch(i -> i.getMethod().getName().equals("update")));
	}

	private static ResultSet uuidRow(UUID value) throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getObject(1, UUID.class)).thenReturn(value);
		return rs;
	}

	/** Columns in the order CheckoutService reads them: book id, seller id, title, price, stock, quantity. */
	private static ResultSet bookRow(long book, long seller, String title, String price, int stock, int quantity)
			throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong(1)).thenReturn(book);
		when(rs.getLong(2)).thenReturn(seller);
		when(rs.getString(3)).thenReturn(title);
		when(rs.getBigDecimal(4)).thenReturn(new BigDecimal(price));
		when(rs.getInt(5)).thenReturn(stock);
		when(rs.getInt(6)).thenReturn(quantity);
		return rs;
	}

	private static UserRepository usersWith(long id) throws Exception {
		var user = new User("Buyer", EMAIL, "hash");
		Field field = User.class.getDeclaredField("id");
		field.setAccessible(true);
		field.set(user, id);
		var users = mock(UserRepository.class);
		when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
		return users;
	}
}

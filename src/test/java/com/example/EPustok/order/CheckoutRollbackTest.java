package com.example.EPustok.order;

import com.example.EPustok.identity.User;
import com.example.EPustok.identity.UserRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Answers;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

/**
 * Checks the checkout transaction boundary without a database. The cart has two lines that pass the
 * pre-checks: book 11 decrements, then book 12's conditional UPDATE finds its stock already gone.
 * The transaction manager must roll back, never commit. Rolling back undoes the first decrement in PostgreSQL.
 * The row-level proof against real PostgreSQL is CheckoutConcurrencyIT (Testcontainers).
 */
class CheckoutRollbackTest {
	private static final String EMAIL = "b@example.com";

	@Configuration
	@EnableTransactionManagement
	static class TransactionConfig {
		@Bean PlatformTransactionManager transactionManager() { return mock(PlatformTransactionManager.class); }
	}

	@Test void failureMidCheckoutRollsBackAndKeepsCart() throws Exception {
		try (var context = context(12L)) {
			var tm = context.getBean(PlatformTransactionManager.class);
			when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
			var jdbc = context.getBean(JdbcTemplate.class);

			var ex = assertThrows(ResponseStatusException.class,
					() -> context.getBean(CheckoutService.class).place(EMAIL, "Recipient", "01234567890", "Dhaka", UUID.randomUUID()));

			assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
			assertEquals("Stock changed for Second. Update your cart.", ex.getReason());
			// Both conditional decrements were attempted: the first succeeded in the transaction, the second matched no row.
			verify(jdbc, times(2)).update(startsWith("UPDATE book SET stock"), any(Object[].class));
			// Stock is reserved before any order row, so the failure happens before the order INSERT.
			verify(jdbc, never()).update(startsWith("INSERT INTO purchase_order"), ArgumentMatchers.<Object>any());
			verify(tm).rollback(any(TransactionStatus.class));
			verify(tm, never()).commit(any(TransactionStatus.class));
			// The cart is cleared only after every decrement succeeds, so this failure must not reach that step.
			verify(jdbc, never()).update(startsWith("DELETE FROM cart_item"), ArgumentMatchers.<Object>any());
		}
	}

	@Test void successfulCheckoutCommits() throws Exception {
		try (var context = context(-1L)) {
			var tm = context.getBean(PlatformTransactionManager.class);
			when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

			context.getBean(CheckoutService.class).place(EMAIL, "Recipient", "01234567890", "Dhaka", UUID.randomUUID());

			verify(tm).commit(any(TransactionStatus.class));
			verify(tm, never()).rollback(any(TransactionStatus.class));
		}
	}

	/** Real Spring transaction proxy around CheckoutService. The JdbcTemplate fake fails the decrement for failingBook. */
	private static AnnotationConfigApplicationContext context(long failingBook) throws Exception {
		var context = new AnnotationConfigApplicationContext();
		context.register(TransactionConfig.class);
		var users = usersWith(7L);
		context.registerBean(UserRepository.class, () -> users);
		context.registerBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class, fakeDatabase(failingBook)));
		context.registerBean(CheckoutService.class);
		context.refresh();
		return context;
	}

	/** Cart rows: book 11 and book 12, each quantity 1. Only the decrement for failingBook reports zero rows. */
	private static org.mockito.stubbing.Answer<Object> fakeDatabase(long failingBook) {
		return invocation -> {
			Object[] args = invocation.getArguments();
			String name = invocation.getMethod().getName();
			if ("update".equals(name)) {
				if (args[0] instanceof String sql && sql.startsWith("UPDATE book SET stock")) {
					return args[2].equals(failingBook) ? 0 : 1;
				}
				return 1;
			}
			boolean rowQuery = "query".equals(name) && args.length > 1 && args[1] instanceof RowMapper;
			if (!rowQuery) {
				return Answers.RETURNS_DEFAULTS.answer(invocation);
			}
			String sql = (String) args[0];
			if (sql.contains("FROM cart_item")) {
				@SuppressWarnings("unchecked")
				RowMapper<Object> mapper = (RowMapper<Object>) args[1];
				return List.of(
						mapper.mapRow(bookRow(11L, 8L, "First", "10.00", 1), 1),
						mapper.mapRow(bookRow(12L, 8L, "Second", "5.00", 1), 1));
			}
			return List.of();
		};
	}

	/** Columns in the order CheckoutService reads them: book id, seller id, title, price, quantity. */
	private static ResultSet bookRow(long book, long seller, String title, String price, int quantity) throws Exception {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getLong(1)).thenReturn(book);
		when(rs.getLong(2)).thenReturn(seller);
		when(rs.getString(3)).thenReturn(title);
		when(rs.getBigDecimal(4)).thenReturn(new BigDecimal(price));
		when(rs.getInt(5)).thenReturn(quantity);
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

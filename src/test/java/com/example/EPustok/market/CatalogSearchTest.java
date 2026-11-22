package com.example.EPustok.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

/** Title/author search builds a LIKE predicate over both columns and escapes wildcard characters. */
class CatalogSearchTest {
    @SuppressWarnings("unchecked")
    @Test void searchMatchesTitleOrAuthorWithEscapedWildcards() {
        Root<Book> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate any = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(any);
        when(cb.and(any(Predicate.class), any(Predicate.class))).thenReturn(any);
        Path<String> title = mock(Path.class);
        Path<String> author = mock(Path.class);
        when(root.get("title")).thenReturn((Path) title);
        when(root.get("author")).thenReturn((Path) author);
        Expression<String> lowerTitle = mock(Expression.class);
        Expression<String> lowerAuthor = mock(Expression.class);
        when(cb.lower((Expression<String>) any())).thenReturn(lowerTitle, lowerAuthor);
        when(cb.like(any(Expression.class), anyString(), eq('\\'))).thenReturn(any);
        when(cb.or(any(Predicate.class), any(Predicate.class))).thenReturn(any);

        Specification<Book> spec = CatalogController.bookFilter(null, "50%_");
        assertNotNull(spec.toPredicate(root, (CriteriaQuery<Book>) query, cb));

        // "50%_" becomes the literal pattern %50\%\_% so the wildcards match only themselves.
        verify(cb, org.mockito.Mockito.times(2)).like(any(Expression.class), eq("%50\\%\\_%"), eq('\\'));
    }

    @Test void blankSearchAddsNoFilterOnTheDatabase() {
        Root<Book> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate any = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(any);
        Specification<Book> spec = CatalogController.bookFilter(null, "");
        spec.toPredicate(root, (CriteriaQuery<Book>) query, cb);
        verify(cb, org.mockito.Mockito.never()).like(any(Expression.class), anyString(), any(Character.class));
    }
}

package com.example.EPustok.market;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;

/**
 * Book finders load the category in the same query (EntityGraph) so catalog and home pages do not issue one extra
 * select per book. Book.category stays EAGER, so findById and other owners' code are unchanged.
 */
public interface Books extends JpaRepository<Book,Long>, JpaSpecificationExecutor<Book> {
    @EntityGraph(attributePaths = "category")
    List<Book> findBySellerId(Long sellerId);

    boolean existsByCategoryId(Long categoryId);

    @EntityGraph(attributePaths = "category")
    Page<Book> findAll(Specification<Book> spec, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    List<Book> findTop12ByOrderByIdDesc();
}

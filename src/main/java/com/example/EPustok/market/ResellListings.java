package com.example.EPustok.market;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResellListings extends JpaRepository<ResellListing, Long>, JpaSpecificationExecutor<ResellListing> {
    List<ResellListing> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<ResellListing> findByStatusOrderByCreatedAtDesc(String status);

    /** Row lock held until the transaction ends. Offers take it so they serialize with accept and close. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from ResellListing l where l.id = :id")
    Optional<ResellListing> findByIdForUpdate(@Param("id") long id);

    /** Guarded state change: succeeds only while the listing is still AVAILABLE and owned by ownerId. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ResellListing l set l.status = 'CLOSED' where l.id = :id and l.userId = :ownerId and l.status = 'AVAILABLE'")
    int closeAvailable(@Param("id") long id, @Param("ownerId") long ownerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ResellListing l set l.bookCondition = :condition, l.price = :price, l.description = :description "
            + "where l.id = :id and l.userId = :ownerId and l.status = 'AVAILABLE'")
    int updateAvailable(@Param("id") long id, @Param("ownerId") long ownerId, @Param("condition") String condition,
            @Param("price") BigDecimal price, @Param("description") String description);

    /**
     * Deletes only an AVAILABLE listing. Checkout closes a listing in the same transaction that inserts its
     * order_item, so the status test also protects against a concurrent purchase. The NOT EXISTS test covers
     * listings that already belong to an order (order_item.listing_id, V10).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from marketplace_resell_listings where id = :id and user_id = :ownerId and status = 'AVAILABLE' "
            + "and not exists (select 1 from order_item oi where oi.listing_id = marketplace_resell_listings.id)",
            nativeQuery = true)
    int deleteAvailableNotOrdered(@Param("id") long id, @Param("ownerId") long ownerId);

    @Query(value = "select exists (select 1 from order_item oi where oi.listing_id = :id)", nativeQuery = true)
    boolean isInOrder(@Param("id") long id);
}

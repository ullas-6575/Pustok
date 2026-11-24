package com.example.EPustok.market;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExchangeOffers extends JpaRepository<ExchangeOffer, Long> {
    boolean existsByListingIdAndOffererIdAndStatus(Long listingId, Long offererId, ExchangeState status);

    /** Offers received on the given listings, newest first (owner view). */
    List<ExchangeOffer> findByListingIdInOrderByCreatedAtDesc(Collection<Long> listingIds);

    /** Offers the user has made, newest first (offerer view). */
    List<ExchangeOffer> findByOffererIdOrderByCreatedAtDesc(Long offererId);

    /** Guarded state change: succeeds only if the offer is still in state {@code from}. Returns 0 if it moved on. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ExchangeOffer o set o.status = :to, o.version = o.version + 1 "
            + "where o.id = :id and o.status = :from")
    int transition(@Param("id") long id, @Param("from") ExchangeState from, @Param("to") ExchangeState to);

    /** Rejects every other live offer on the listing when one offer is accepted. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ExchangeOffer o set o.status = :to, o.version = o.version + 1 "
            + "where o.listingId = :listingId and o.id <> :keepId and o.status = :from")
    int rejectOtherProposed(@Param("listingId") long listingId, @Param("keepId") long keepId,
            @Param("from") ExchangeState from, @Param("to") ExchangeState to);

    /** Rejects every live offer on a listing that was closed without an accepted offer. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ExchangeOffer o set o.status = :to, o.version = o.version + 1 "
            + "where o.listingId = :listingId and o.status = :from")
    int rejectAllProposed(@Param("listingId") long listingId, @Param("from") ExchangeState from,
            @Param("to") ExchangeState to);
}

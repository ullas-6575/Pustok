package com.example.EPustok.market;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "marketplace_exchange_offers")
@Getter
public class ExchangeOffer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "offerer_id", nullable = false)
    private Long offererId;

    @Column(name = "offered_title", nullable = false, length = 255)
    private String offeredTitle;

    @Column(name = "offered_author", nullable = false, length = 255)
    private String offeredAuthor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExchangeState status = ExchangeState.PROPOSED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Version
    @Column(nullable = false)
    private long version;

    protected ExchangeOffer() {}

    public ExchangeOffer(Long listingId, Long offererId, String offeredTitle, String offeredAuthor) {
        this.listingId = listingId;
        this.offererId = offererId;
        this.offeredTitle = offeredTitle;
        this.offeredAuthor = offeredAuthor;
        this.status = ExchangeState.PROPOSED;
    }
}

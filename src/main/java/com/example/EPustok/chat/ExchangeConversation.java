package com.example.EPustok.chat;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "exchange_conversation")
@Getter
public class ExchangeConversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "offerer_id", nullable = false)
    private Long offererId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ExchangeConversation() {}

    public ExchangeConversation(Long listingId, Long ownerId, Long offererId) {
        this.listingId = listingId;
        this.ownerId = ownerId;
        this.offererId = offererId;
    }

    public boolean includesUser(Long userId) {
        return userId != null && (userId.equals(ownerId) || userId.equals(offererId));
    }
}

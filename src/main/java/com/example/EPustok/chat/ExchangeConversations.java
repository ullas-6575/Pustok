package com.example.EPustok.chat;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeConversations extends JpaRepository<ExchangeConversation, Long> {
    Optional<ExchangeConversation> findByListingIdAndOffererId(Long listingId, Long offererId);
    List<ExchangeConversation> findByOwnerIdOrOffererIdOrderByCreatedAtDesc(Long ownerId, Long offererId);
}

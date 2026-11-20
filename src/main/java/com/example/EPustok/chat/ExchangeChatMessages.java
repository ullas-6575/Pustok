package com.example.EPustok.chat;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeChatMessages extends JpaRepository<ExchangeChatMessage, Long> {
    List<ExchangeChatMessage> findByConversationIdOrderBySentAtAsc(Long conversationId);
}

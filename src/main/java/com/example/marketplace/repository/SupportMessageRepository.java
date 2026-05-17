package com.example.marketplace.repository;

import com.example.marketplace.entity.SupportConversation;
import com.example.marketplace.entity.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    List<SupportMessage> findByConversationOrderBySentAtAsc(SupportConversation conversation);
    List<SupportMessage> findByConversationAndIdGreaterThanOrderBySentAtAsc(SupportConversation conversation, Long afterId);
}

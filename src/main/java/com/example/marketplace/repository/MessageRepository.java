package com.example.marketplace.repository;

import com.example.marketplace.entity.Conversation;
import com.example.marketplace.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationOrderBySentAtAsc(Conversation conversation);
}

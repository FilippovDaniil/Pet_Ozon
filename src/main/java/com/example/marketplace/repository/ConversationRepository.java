package com.example.marketplace.repository;

import com.example.marketplace.entity.Conversation;
import com.example.marketplace.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findByClientOrderByUpdatedAtDesc(User client);
    List<Conversation> findBySellerOrderByUpdatedAtDesc(User seller);
    Optional<Conversation> findByClientAndSeller(User client, User seller);
}

package com.example.marketplace.repository;

import com.example.marketplace.entity.SupportConversation;
import com.example.marketplace.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupportConversationRepository extends JpaRepository<SupportConversation, Long> {
    Optional<SupportConversation> findByClient(User client);
    List<SupportConversation> findAllByOrderByUpdatedAtDesc();
}

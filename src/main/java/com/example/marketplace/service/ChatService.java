package com.example.marketplace.service;

import com.example.marketplace.dto.response.ConversationResponse;
import com.example.marketplace.dto.response.MessageResponse;
import com.example.marketplace.entity.Conversation;
import com.example.marketplace.entity.Message;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.ConversationRepository;
import com.example.marketplace.repository.MessageRepository;
import com.example.marketplace.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    @Transactional
    public ConversationResponse startConversation(User client, Long sellerId, String messageText) {
        User seller = userRepository.findById(sellerId)
                .filter(u -> u.getRole() == Role.SELLER)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found with id: " + sellerId));

        Conversation conversation = conversationRepository.findByClientAndSeller(client, seller)
                .orElseGet(() -> {
                    Conversation c = new Conversation();
                    c.setClient(client);
                    c.setSeller(seller);
                    return conversationRepository.save(c);
                });

        Message msg = new Message();
        msg.setConversation(conversation);
        msg.setSender(client);
        msg.setContent(messageText);
        messageRepository.save(msg);

        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        log.info("ACTION=CHAT_START clientId={} sellerId={} conversationId={}",
                client.getId(), sellerId, conversation.getId());
        return toConversationResponse(conversation, client);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getMyConversations(User user) {
        List<Conversation> conversations = user.getRole() == Role.CLIENT
                ? conversationRepository.findByClientOrderByUpdatedAtDesc(user)
                : conversationRepository.findBySellerOrderByUpdatedAtDesc(user);
        return conversations.stream().map(c -> toConversationResponse(c, user)).toList();
    }

    @Transactional
    public List<MessageResponse> getMessages(User user, Long conversationId) {
        Conversation conversation = findConversation(conversationId);
        checkParticipant(conversation, user);

        List<Message> messages = messageRepository.findByConversationOrderBySentAtAsc(conversation);

        List<Message> unread = messages.stream()
                .filter(m -> !m.getSender().getId().equals(user.getId()) && !m.isRead())
                .toList();
        if (!unread.isEmpty()) {
            unread.forEach(m -> m.setRead(true));
            messageRepository.saveAll(unread);
        }

        return messages.stream().map(m -> toMessageResponse(m, user)).toList();
    }

    @Transactional
    public MessageResponse sendMessage(User sender, Long conversationId, String content) {
        Conversation conversation = findConversation(conversationId);
        checkParticipant(conversation, sender);

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(content);
        Message saved = messageRepository.save(message);

        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        log.info("ACTION=CHAT_SEND senderId={} conversationId={}", sender.getId(), conversationId);
        return toMessageResponse(saved, sender);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private Conversation findConversation(Long id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + id));
    }

    private void checkParticipant(Conversation conversation, User user) {
        if (!conversation.getClient().getId().equals(user.getId())
                && !conversation.getSeller().getId().equals(user.getId())) {
            throw new AccessDeniedException("Вы не являетесь участником этого диалога");
        }
    }

    private ConversationResponse toConversationResponse(Conversation c, User viewer) {
        ConversationResponse r = new ConversationResponse();
        r.setId(c.getId());
        r.setClientId(c.getClient().getId());
        r.setClientName(displayName(c.getClient()));
        r.setSellerId(c.getSeller().getId());
        r.setSellerName(displayName(c.getSeller()));
        r.setShopName(c.getSeller().getShopName());
        r.setUpdatedAt(c.getUpdatedAt());

        List<Message> all = messageRepository.findByConversationOrderBySentAtAsc(c);
        if (!all.isEmpty()) {
            String text = all.get(all.size() - 1).getContent();
            r.setLastMessage(text.length() > 60 ? text.substring(0, 60) + "…" : text);
        }
        long unread = all.stream()
                .filter(m -> !m.getSender().getId().equals(viewer.getId()) && !m.isRead())
                .count();
        r.setUnreadCount(unread);

        return r;
    }

    private MessageResponse toMessageResponse(Message m, User viewer) {
        MessageResponse r = new MessageResponse();
        r.setId(m.getId());
        r.setSenderId(m.getSender().getId());
        r.setSenderName(displayName(m.getSender()));
        r.setContent(m.getContent());
        r.setSentAt(m.getSentAt());
        r.setRead(m.isRead());
        r.setMine(m.getSender().getId().equals(viewer.getId()));
        return r;
    }

    private String displayName(User user) {
        return user.getFullName() != null && !user.getFullName().isBlank()
                ? user.getFullName()
                : user.getEmail();
    }
}

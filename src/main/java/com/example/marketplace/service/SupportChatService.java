package com.example.marketplace.service;

import com.example.marketplace.dto.response.MessageResponse;
import com.example.marketplace.dto.response.SupportConversationResponse;
import com.example.marketplace.entity.SupportConversation;
import com.example.marketplace.entity.SupportMessage;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.SupportConversationRepository;
import com.example.marketplace.repository.SupportMessageRepository;
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
public class SupportChatService {

    private final SupportConversationRepository conversationRepo;
    private final SupportMessageRepository messageRepo;

    @Transactional
    public SupportConversationResponse getOrCreateMyConversation(User client) {
        SupportConversation conv = conversationRepo.findByClient(client)
                .orElseGet(() -> {
                    SupportConversation c = new SupportConversation();
                    c.setClient(client);
                    SupportConversation saved = conversationRepo.save(c);
                    log.info("ACTION=SUPPORT_CONV_CREATE clientId={}", client.getId());
                    return saved;
                });
        return toResponse(conv, client);
    }

    @Transactional(readOnly = true)
    public List<SupportConversationResponse> getAllConversations() {
        return conversationRepo.findAllByOrderByUpdatedAtDesc().stream()
                .map(c -> toResponseForAdmin(c))
                .toList();
    }

    @Transactional
    public List<MessageResponse> getMessages(User user, Long conversationId) {
        SupportConversation conv = findConversation(conversationId);
        checkAccess(conv, user);

        List<SupportMessage> messages = messageRepo.findByConversationOrderBySentAtAsc(conv);

        List<SupportMessage> unread = messages.stream()
                .filter(m -> !m.getSender().getId().equals(user.getId()) && !m.isRead())
                .toList();
        if (!unread.isEmpty()) {
            unread.forEach(m -> m.setRead(true));
            messageRepo.saveAll(unread);
        }

        return messages.stream().map(m -> toMessageResponse(m, user)).toList();
    }

    @Transactional
    public List<MessageResponse> pollMessages(User user, Long conversationId, Long afterId) {
        SupportConversation conv = findConversation(conversationId);
        checkAccess(conv, user);

        List<SupportMessage> messages = messageRepo
                .findByConversationAndIdGreaterThanOrderBySentAtAsc(conv, afterId);

        List<SupportMessage> unread = messages.stream()
                .filter(m -> !m.getSender().getId().equals(user.getId()) && !m.isRead())
                .toList();
        if (!unread.isEmpty()) {
            unread.forEach(m -> m.setRead(true));
            messageRepo.saveAll(unread);
        }

        return messages.stream().map(m -> toMessageResponse(m, user)).toList();
    }

    @Transactional
    public MessageResponse sendMessage(User sender, Long conversationId, String content) {
        SupportConversation conv = findConversation(conversationId);
        checkAccess(conv, sender);

        SupportMessage msg = new SupportMessage();
        msg.setConversation(conv);
        msg.setSender(sender);
        msg.setContent(content);
        SupportMessage saved = messageRepo.save(msg);

        conv.setUpdatedAt(LocalDateTime.now());
        conversationRepo.save(conv);

        log.info("ACTION=SUPPORT_MSG_SEND senderId={} conversationId={}", sender.getId(), conversationId);
        return toMessageResponse(saved, sender);
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private SupportConversation findConversation(Long id) {
        return conversationRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Support conversation not found: " + id));
    }

    private void checkAccess(SupportConversation conv, User user) {
        if (user.getRole() == Role.ADMIN) return;
        if (!conv.getClient().getId().equals(user.getId())) {
            throw new AccessDeniedException("Нет доступа к этому диалогу");
        }
    }

    private SupportConversationResponse toResponse(SupportConversation conv, User viewer) {
        SupportConversationResponse r = new SupportConversationResponse();
        r.setId(conv.getId());
        r.setClientId(conv.getClient().getId());
        r.setClientName(displayName(conv.getClient()));
        r.setUpdatedAt(conv.getUpdatedAt());

        List<SupportMessage> all = messageRepo.findByConversationOrderBySentAtAsc(conv);
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

    private SupportConversationResponse toResponseForAdmin(SupportConversation conv) {
        SupportConversationResponse r = new SupportConversationResponse();
        r.setId(conv.getId());
        r.setClientId(conv.getClient().getId());
        r.setClientName(displayName(conv.getClient()));
        r.setUpdatedAt(conv.getUpdatedAt());

        List<SupportMessage> all = messageRepo.findByConversationOrderBySentAtAsc(conv);
        if (!all.isEmpty()) {
            String text = all.get(all.size() - 1).getContent();
            r.setLastMessage(text.length() > 60 ? text.substring(0, 60) + "…" : text);
        }
        // Для админа — непрочитанные = сообщения от клиента, которые не прочитал ни один сотрудник.
        // Упрощение: считаем все непрочитанные сообщения не от админов.
        long unread = all.stream()
                .filter(m -> m.getSender().getRole() != Role.ADMIN && !m.isRead())
                .count();
        r.setUnreadCount(unread);
        return r;
    }

    private MessageResponse toMessageResponse(SupportMessage m, User viewer) {
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

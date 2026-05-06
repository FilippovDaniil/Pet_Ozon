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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock ConversationRepository conversationRepository;
    @Mock MessageRepository messageRepository;
    @Mock UserRepository userRepository;

    @InjectMocks ChatService chatService;

    // ── helpers ───────────────────────────────────────────────────────────────

    private User makeClient() {
        User u = new User();
        u.setId(1L);
        u.setEmail("client@test.com");
        u.setFullName("Клиент Иван");
        u.setRole(Role.CLIENT);
        return u;
    }

    private User makeSeller() {
        User u = new User();
        u.setId(2L);
        u.setEmail("seller@test.com");
        u.setFullName("Продавец Мария");
        u.setShopName("Магазин Марии");
        u.setRole(Role.SELLER);
        return u;
    }

    private Conversation makeConversation(User client, User seller) {
        Conversation c = new Conversation();
        c.setId(1L);
        c.setClient(client);
        c.setSeller(seller);
        c.setUpdatedAt(LocalDateTime.now());
        return c;
    }

    private Message makeMessage(Conversation conv, User sender, String content, boolean read) {
        Message m = new Message();
        m.setId(1L);
        m.setConversation(conv);
        m.setSender(sender);
        m.setContent(content);
        m.setSentAt(LocalDateTime.now());
        m.setRead(read);
        return m;
    }

    // ── startConversation ─────────────────────────────────────────────────────

    @Test
    void startConversation_newConversation_createsAndReturnsIt() {
        User client = makeClient();
        User seller = makeSeller();
        Conversation conversation = makeConversation(client, seller);
        Message msg = makeMessage(conversation, client, "Привет!", false);

        when(userRepository.findById(2L)).thenReturn(Optional.of(seller));
        when(conversationRepository.findByClientAndSeller(client, seller)).thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenReturn(conversation);
        when(messageRepository.save(any(Message.class))).thenReturn(msg);
        when(messageRepository.findByConversationOrderBySentAtAsc(conversation)).thenReturn(List.of(msg));

        ConversationResponse result = chatService.startConversation(client, 2L, "Привет!");

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getSellerId()).isEqualTo(2L);
        assertThat(result.getSellerName()).isEqualTo("Продавец Мария");
        assertThat(result.getShopName()).isEqualTo("Магазин Марии");
        verify(messageRepository).save(any(Message.class));
    }

    @Test
    void startConversation_existingConversation_reusesIt() {
        User client = makeClient();
        User seller = makeSeller();
        Conversation existing = makeConversation(client, seller);
        Message msg = makeMessage(existing, client, "Снова пишу", false);

        when(userRepository.findById(2L)).thenReturn(Optional.of(seller));
        when(conversationRepository.findByClientAndSeller(client, seller)).thenReturn(Optional.of(existing));
        when(messageRepository.save(any(Message.class))).thenReturn(msg);
        when(conversationRepository.save(existing)).thenReturn(existing);
        when(messageRepository.findByConversationOrderBySentAtAsc(existing)).thenReturn(List.of(msg));

        ConversationResponse result = chatService.startConversation(client, 2L, "Снова пишу");

        assertThat(result.getId()).isEqualTo(existing.getId());
        // Новую conversation не должны создавать — только переиспользовать существующую
        verify(conversationRepository, never()).save(argThat(c -> c.getId() == null));
    }

    @Test
    void startConversation_sellerNotFound_throwsException() {
        User client = makeClient();
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.startConversation(client, 99L, "Привет"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void startConversation_targetIsNotSeller_throwsException() {
        User client = makeClient();
        User anotherClient = makeClient();
        anotherClient.setId(3L);
        anotherClient.setRole(Role.CLIENT);

        when(userRepository.findById(3L)).thenReturn(Optional.of(anotherClient));

        // Клиент не имеет роли SELLER — должно быть исключение
        assertThatThrownBy(() -> chatService.startConversation(client, 3L, "Привет"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getMyConversations ────────────────────────────────────────────────────

    @Test
    void getMyConversations_client_returnsClientConversations() {
        User client = makeClient();
        User seller = makeSeller();
        Conversation conv = makeConversation(client, seller);

        when(conversationRepository.findByClientOrderByUpdatedAtDesc(client)).thenReturn(List.of(conv));
        when(messageRepository.findByConversationOrderBySentAtAsc(conv)).thenReturn(List.of());

        List<ConversationResponse> result = chatService.getMyConversations(client);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getClientId()).isEqualTo(1L);
        verify(conversationRepository).findByClientOrderByUpdatedAtDesc(client);
        verify(conversationRepository, never()).findBySellerOrderByUpdatedAtDesc(any());
    }

    @Test
    void getMyConversations_seller_returnsSellerConversations() {
        User client = makeClient();
        User seller = makeSeller();
        Conversation conv = makeConversation(client, seller);

        when(conversationRepository.findBySellerOrderByUpdatedAtDesc(seller)).thenReturn(List.of(conv));
        when(messageRepository.findByConversationOrderBySentAtAsc(conv)).thenReturn(List.of());

        List<ConversationResponse> result = chatService.getMyConversations(seller);

        assertThat(result).hasSize(1);
        verify(conversationRepository).findBySellerOrderByUpdatedAtDesc(seller);
        verify(conversationRepository, never()).findByClientOrderByUpdatedAtDesc(any());
    }

    @Test
    void getMyConversations_withUnreadMessages_returnsCorrectUnreadCount() {
        User client = makeClient();
        User seller = makeSeller();
        Conversation conv = makeConversation(client, seller);

        // Два непрочитанных сообщения от продавца
        Message unread1 = makeMessage(conv, seller, "Есть вопросы?", false);
        Message unread2 = makeMessage(conv, seller, "Напишите нам", false);

        when(conversationRepository.findByClientOrderByUpdatedAtDesc(client)).thenReturn(List.of(conv));
        when(messageRepository.findByConversationOrderBySentAtAsc(conv)).thenReturn(List.of(unread1, unread2));

        List<ConversationResponse> result = chatService.getMyConversations(client);

        assertThat(result.get(0).getUnreadCount()).isEqualTo(2L);
    }

    @Test
    void getMyConversations_withLastMessage_setsLastMessageField() {
        User client = makeClient();
        User seller = makeSeller();
        Conversation conv = makeConversation(client, seller);
        Message msg = makeMessage(conv, client, "Хочу купить ваш товар", false);

        when(conversationRepository.findByClientOrderByUpdatedAtDesc(client)).thenReturn(List.of(conv));
        when(messageRepository.findByConversationOrderBySentAtAsc(conv)).thenReturn(List.of(msg));

        List<ConversationResponse> result = chatService.getMyConversations(client);

        assertThat(result.get(0).getLastMessage()).isEqualTo("Хочу купить ваш товар");
    }

    // ── getMessages ───────────────────────────────────────────────────────────

    @Test
    void getMessages_participant_returnsMessages() {
        User client = makeClient();
        User seller = makeSeller();
        Conversation conv = makeConversation(client, seller);
        Message msg = makeMessage(conv, seller, "Добро пожаловать!", true);

        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));
        when(messageRepository.findByConversationOrderBySentAtAsc(conv)).thenReturn(List.of(msg));

        List<MessageResponse> result = chatService.getMessages(client, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).isEqualTo("Добро пожаловать!");
        assertThat(result.get(0).isMine()).isFalse(); // отправлен продавцом, просматривает клиент
    }

    @Test
    void getMessages_marksIncomingMessagesAsRead() {
        User client = makeClient();
        User seller = makeSeller();
        Conversation conv = makeConversation(client, seller);
        Message unread = makeMessage(conv, seller, "Привет!", false);

        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));
        when(messageRepository.findByConversationOrderBySentAtAsc(conv)).thenReturn(List.of(unread));

        chatService.getMessages(client, 1L);

        // Сообщение от продавца должно быть помечено прочитанным
        assertThat(unread.isRead()).isTrue();
        verify(messageRepository).saveAll(any());
    }

    @Test
    void getMessages_ownMessages_notMarkedAsRead() {
        User client = makeClient();
        User seller = makeSeller();
        Conversation conv = makeConversation(client, seller);
        // Сообщение от самого клиента — не должно помечаться
        Message ownMsg = makeMessage(conv, client, "Моё сообщение", false);

        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));
        when(messageRepository.findByConversationOrderBySentAtAsc(conv)).thenReturn(List.of(ownMsg));

        chatService.getMessages(client, 1L);

        // Своё сообщение не помечается прочитанным (и saveAll не вызывается)
        assertThat(ownMsg.isRead()).isFalse();
        verify(messageRepository, never()).saveAll(any());
    }

    @Test
    void getMessages_notParticipant_throwsAccessDenied() {
        User client = makeClient();
        User seller = makeSeller();
        Conversation conv = makeConversation(client, seller);

        User outsider = new User();
        outsider.setId(99L);
        outsider.setRole(Role.CLIENT);

        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));

        assertThatThrownBy(() -> chatService.getMessages(outsider, 1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getMessages_conversationNotFound_throwsException() {
        User client = makeClient();
        when(conversationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getMessages(client, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── sendMessage ───────────────────────────────────────────────────────────

    @Test
    void sendMessage_clientSendsMessage_savesAndReturns() {
        User client = makeClient();
        User seller = makeSeller();
        Conversation conv = makeConversation(client, seller);
        Message saved = makeMessage(conv, client, "Хочу купить!", false);

        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));
        when(messageRepository.save(any(Message.class))).thenReturn(saved);
        when(conversationRepository.save(conv)).thenReturn(conv);

        MessageResponse result = chatService.sendMessage(client, 1L, "Хочу купить!");

        assertThat(result.getContent()).isEqualTo("Хочу купить!");
        assertThat(result.isMine()).isTrue();
        assertThat(result.getSenderName()).isEqualTo("Клиент Иван");
        verify(messageRepository).save(any(Message.class));
    }

    @Test
    void sendMessage_sellerReplies_savesAndReturns() {
        User client = makeClient();
        User seller = makeSeller();
        Conversation conv = makeConversation(client, seller);
        Message reply = makeMessage(conv, seller, "Да, есть в наличии!", false);

        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));
        when(messageRepository.save(any(Message.class))).thenReturn(reply);
        when(conversationRepository.save(conv)).thenReturn(conv);

        MessageResponse result = chatService.sendMessage(seller, 1L, "Да, есть в наличии!");

        assertThat(result.isMine()).isTrue(); // продавец — отправитель, он же просматриватель
        assertThat(result.getSenderName()).isEqualTo("Продавец Мария");
    }

    @Test
    void sendMessage_notParticipant_throwsAccessDenied() {
        User client = makeClient();
        User seller = makeSeller();
        Conversation conv = makeConversation(client, seller);

        User outsider = new User();
        outsider.setId(99L);

        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));

        assertThatThrownBy(() -> chatService.sendMessage(outsider, 1L, "Взломать"))
                .isInstanceOf(AccessDeniedException.class);

        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendMessage_conversationNotFound_throwsException() {
        User client = makeClient();
        when(conversationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(client, 99L, "Привет"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

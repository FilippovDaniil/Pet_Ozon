package com.example.marketplace.controller;

import com.example.marketplace.config.SecurityConfig;
import com.example.marketplace.config.TestSecurityConfig;
import com.example.marketplace.dto.response.ConversationResponse;
import com.example.marketplace.dto.response.MessageResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.entity.enums.Role;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.security.JwtAuthenticationFilter;
import com.example.marketplace.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = ChatController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestSecurityConfig.class)
class ChatControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ChatService chatService;

    private User mockClient() {
        User u = new User();
        u.setId(1L);
        u.setEmail("client@example.com");
        u.setRole(Role.CLIENT);
        return u;
    }

    private User mockSeller() {
        User u = new User();
        u.setId(2L);
        u.setEmail("seller@example.com");
        u.setRole(Role.SELLER);
        return u;
    }

    private ConversationResponse makeConversationResponse(Long id) {
        ConversationResponse r = new ConversationResponse();
        r.setId(id);
        r.setClientId(1L);
        r.setClientName("Клиент Иван");
        r.setSellerId(2L);
        r.setSellerName("Продавец Мария");
        r.setShopName("Магазин Марии");
        r.setUpdatedAt(LocalDateTime.now());
        r.setUnreadCount(0L);
        return r;
    }

    private MessageResponse makeMessageResponse(Long id, boolean mine) {
        MessageResponse r = new MessageResponse();
        r.setId(id);
        r.setSenderId(mine ? 1L : 2L);
        r.setSenderName(mine ? "Клиент Иван" : "Продавец Мария");
        r.setContent("Тестовое сообщение");
        r.setSentAt(LocalDateTime.now());
        r.setMine(mine);
        return r;
    }

    // ── POST /api/chat/conversations ──────────────────────────────────────────

    @Test
    void startConversation_validRequest_returns201() throws Exception {
        when(chatService.startConversation(any(User.class), eq(2L), eq("Привет!")))
                .thenReturn(makeConversationResponse(1L));

        mockMvc.perform(post("/api/chat/conversations")
                        .with(user(mockClient()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellerId\":2,\"message\":\"Привет!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sellerId").value(2))
                .andExpect(jsonPath("$.shopName").value("Магазин Марии"));
    }

    @Test
    void startConversation_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/chat/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellerId\":2,\"message\":\"Привет!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void startConversation_missingSellerId_returns400() throws Exception {
        // sellerId — @NotNull, без него валидация падает
        mockMvc.perform(post("/api/chat/conversations")
                        .with(user(mockClient()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Привет!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void startConversation_emptyMessage_returns400() throws Exception {
        // message — @NotBlank, пустая строка не допускается
        mockMvc.perform(post("/api/chat/conversations")
                        .with(user(mockClient()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellerId\":2,\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void startConversation_sellerNotFound_returns404() throws Exception {
        when(chatService.startConversation(any(), eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Seller not found with id: 99"));

        mockMvc.perform(post("/api/chat/conversations")
                        .with(user(mockClient()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellerId\":99,\"message\":\"Привет!\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Seller not found with id: 99"));
    }

    // ── GET /api/chat/conversations ───────────────────────────────────────────

    @Test
    void getConversations_client_returns200WithList() throws Exception {
        when(chatService.getMyConversations(any(User.class)))
                .thenReturn(List.of(makeConversationResponse(1L), makeConversationResponse(2L)));

        mockMvc.perform(get("/api/chat/conversations")
                        .with(user(mockClient())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    void getConversations_seller_returns200WithList() throws Exception {
        when(chatService.getMyConversations(any(User.class)))
                .thenReturn(List.of(makeConversationResponse(1L)));

        mockMvc.perform(get("/api/chat/conversations")
                        .with(user(mockSeller())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getConversations_empty_returns200WithEmptyList() throws Exception {
        when(chatService.getMyConversations(any(User.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/chat/conversations")
                        .with(user(mockClient())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getConversations_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/chat/conversations"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/chat/conversations/{id}/messages ─────────────────────────────

    @Test
    void getMessages_participant_returns200WithMessages() throws Exception {
        when(chatService.getMessages(any(User.class), eq(1L)))
                .thenReturn(List.of(
                        makeMessageResponse(1L, false),
                        makeMessageResponse(2L, true)
                ));

        mockMvc.perform(get("/api/chat/conversations/1/messages")
                        .with(user(mockClient())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].mine").value(false))
                .andExpect(jsonPath("$[1].mine").value(true))
                .andExpect(jsonPath("$[0].content").value("Тестовое сообщение"));
    }

    @Test
    void getMessages_nonParticipant_returns403() throws Exception {
        when(chatService.getMessages(any(User.class), eq(1L)))
                .thenThrow(new AccessDeniedException("Вы не являетесь участником этого диалога"));

        User outsider = new User();
        outsider.setId(99L);
        outsider.setRole(Role.CLIENT);

        mockMvc.perform(get("/api/chat/conversations/1/messages")
                        .with(user(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMessages_conversationNotFound_returns404() throws Exception {
        when(chatService.getMessages(any(User.class), eq(99L)))
                .thenThrow(new ResourceNotFoundException("Conversation not found: 99"));

        mockMvc.perform(get("/api/chat/conversations/99/messages")
                        .with(user(mockClient())))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMessages_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/chat/conversations/1/messages"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/chat/conversations/{id}/messages ────────────────────────────

    @Test
    void sendMessage_validRequest_returns201() throws Exception {
        when(chatService.sendMessage(any(User.class), eq(1L), eq("Хочу купить!")))
                .thenReturn(makeMessageResponse(3L, true));

        mockMvc.perform(post("/api/chat/conversations/1/messages")
                        .with(user(mockClient()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Хочу купить!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.mine").value(true));
    }

    @Test
    void sendMessage_emptyContent_returns400() throws Exception {
        // content — @NotBlank: пустая строка → 400
        mockMvc.perform(post("/api/chat/conversations/1/messages")
                        .with(user(mockClient()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendMessage_nonParticipant_returns403() throws Exception {
        when(chatService.sendMessage(any(User.class), eq(1L), any()))
                .thenThrow(new AccessDeniedException("Вы не являетесь участником этого диалога"));

        User outsider = new User();
        outsider.setId(99L);
        outsider.setRole(Role.CLIENT);

        mockMvc.perform(post("/api/chat/conversations/1/messages")
                        .with(user(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Взломать!\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sendMessage_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/chat/conversations/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Привет!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sendMessage_withoutBody_returns415() throws Exception {
        // Нет Content-Type: application/json → 415
        mockMvc.perform(post("/api/chat/conversations/1/messages")
                        .with(user(mockClient())))
                .andExpect(status().isUnsupportedMediaType());
    }
}

package com.example.marketplace.controller;

import com.example.marketplace.dto.request.SendMessageRequest;
import com.example.marketplace.dto.request.StartConversationRequest;
import com.example.marketplace.dto.response.ConversationResponse;
import com.example.marketplace.dto.response.MessageResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/chat", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** Клиент начинает диалог с продавцом. Если диалог уже существует — переиспользует его. */
    @PostMapping(value = "/conversations", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse startConversation(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody StartConversationRequest request) {
        return chatService.startConversation(user, request.getSellerId(), request.getMessage());
    }

    /** Список моих диалогов — для клиента это диалоги где он покупатель, для продавца — где он продавец. */
    @GetMapping("/conversations")
    public List<ConversationResponse> getConversations(@AuthenticationPrincipal User user) {
        return chatService.getMyConversations(user);
    }

    /** Сообщения в диалоге. Входящие непрочитанные помечаются прочитанными. */
    @GetMapping("/conversations/{id}/messages")
    public List<MessageResponse> getMessages(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return chatService.getMessages(user, id);
    }

    /** Отправить сообщение в диалог. Доступно только участникам диалога. */
    @PostMapping(value = "/conversations/{id}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse sendMessage(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody SendMessageRequest request) {
        return chatService.sendMessage(user, id, request.getContent());
    }
}

package com.example.marketplace.controller;

import com.example.marketplace.dto.request.SendMessageRequest;
import com.example.marketplace.dto.response.MessageResponse;
import com.example.marketplace.dto.response.SupportConversationResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.service.SupportChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/support", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SupportChatController {

    private final SupportChatService supportChatService;

    /** Клиент создаёт или получает свой диалог с поддержкой. */
    @PostMapping(value = "/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENT')")
    public SupportConversationResponse startConversation(@AuthenticationPrincipal User user) {
        return supportChatService.getOrCreateMyConversation(user);
    }

    /** Все диалоги поддержки — только для администратора. */
    @GetMapping("/conversations")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SupportConversationResponse> getAllConversations() {
        return supportChatService.getAllConversations();
    }

    /** Сообщения в диалоге. Входящие непрочитанные помечаются прочитанными. */
    @GetMapping("/conversations/{id}/messages")
    public List<MessageResponse> getMessages(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return supportChatService.getMessages(user, id);
    }

    /** Polling новых сообщений (id > after). */
    @GetMapping("/conversations/{id}/messages/poll")
    public List<MessageResponse> pollMessages(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") Long after) {
        return supportChatService.pollMessages(user, id, after);
    }

    /** Отправить сообщение в диалог. */
    @PostMapping(value = "/conversations/{id}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse sendMessage(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody SendMessageRequest request) {
        return supportChatService.sendMessage(user, id, request.getContent());
    }
}

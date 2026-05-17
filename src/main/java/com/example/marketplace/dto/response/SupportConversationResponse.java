package com.example.marketplace.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SupportConversationResponse {
    private Long id;
    private Long clientId;
    private String clientName;
    private String lastMessage;
    private LocalDateTime updatedAt;
    private long unreadCount;
}

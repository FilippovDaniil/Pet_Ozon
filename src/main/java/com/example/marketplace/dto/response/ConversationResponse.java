package com.example.marketplace.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationResponse {
    private Long id;
    private Long clientId;
    private String clientName;
    private Long sellerId;
    private String sellerName;
    private String shopName;
    private String lastMessage;
    private LocalDateTime updatedAt;
    private long unreadCount;
}

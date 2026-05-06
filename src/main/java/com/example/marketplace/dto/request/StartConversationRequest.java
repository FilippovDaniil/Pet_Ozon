package com.example.marketplace.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StartConversationRequest {

    @NotNull(message = "Укажите продавца")
    private Long sellerId;

    @NotBlank(message = "Сообщение не может быть пустым")
    private String message;
}

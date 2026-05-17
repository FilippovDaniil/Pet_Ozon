package com.example.marketplace.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class EmailLogDto {
    private Long id;
    private String recipient;
    private String subject;
    private LocalDateTime sentAt;
    private boolean success;
    private String errorMessage;
}

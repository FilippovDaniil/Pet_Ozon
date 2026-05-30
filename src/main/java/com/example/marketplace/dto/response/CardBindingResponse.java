package com.example.marketplace.dto.response;

/**
 * Привязанная карта пользователя для отображения в UI.
 * Содержит только безопасные для показа данные — маскированный номер и срок.
 * Сам bindingId на фронт не отдаётся.
 */
public record CardBindingResponse(
        Long    id,
        String  maskedPan,      // маскированный номер: «411111**1111»
        String  expiry,         // MM/YYYY
        boolean isDefault,      // карта по умолчанию для авто-списаний
        String  createdAt
) {}

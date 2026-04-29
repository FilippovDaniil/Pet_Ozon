package com.example.marketplace.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Ответ с данными одного отзыва.
 *
 * Не включает полный объект User и Product — только нужные поля (id, имя).
 * Это предотвращает циклические ссылки и не раскрывает лишних данных.
 */
@Data
public class ReviewResponse {

    private Long id;

    // Кто написал: id + имя пользователя (без email, пароля и других приватных полей)
    private Long userId;
    private String userFullName;

    // Оценка от 1 до 5
    private int rating;

    // Текст отзыва (может быть null)
    private String comment;

    private LocalDateTime createdAt;
}

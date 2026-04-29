package com.example.marketplace.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Отзыв покупателя на товар.
 *
 * Связи:
 *   Review ──N:1──► Product   (много отзывов → один товар)
 *   Review ──N:1──► User      (много отзывов → один автор)
 *
 * Ограничение: один пользователь может оставить только один отзыв на товар.
 * Реализуется через уникальный составной индекс (product_id + user_id).
 *
 * rating: число от 1 до 5 (проверяется в DTO через @Min/@Max).
 * comment: необязательный текст отзыва.
 */
@Entity
@Table(
    name = "reviews",
    uniqueConstraints = {
        // Уникальный составной ключ: один пользователь — один отзыв на товар.
        // Если нарушить — PostgreSQL выбросит DataIntegrityViolationException.
        @UniqueConstraint(
            name = "uk_review_product_user",
            columnNames = {"product_id", "user_id"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // N отзывов → 1 товар. FetchType.LAZY: данные о товаре не грузятся, пока не нужны.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // N отзывов → 1 автор (пользователь).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Оценка от 1 до 5. Валидация в ReviewRequest (@Min(1) @Max(5)).
    @Column(nullable = false)
    private int rating;

    // Текст отзыва — необязателен (можно поставить только звёзды без комментария).
    @Column(length = 1000)
    private String comment;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

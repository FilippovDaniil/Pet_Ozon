package com.example.marketplace.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Запрос на привязку карты через Альфа Банк.
 *
 * Создаётся при POST /api/cards/bind. После того как клиент проходит форму
 * и банк редиректит на card-bind-callback — статус меняется на COMPLETED/FAILED,
 * карта сохраняется в card_bindings, а списание 1₽ отменяется через reverse.do.
 */
@Entity
@Table(name = "card_bind_requests")
@Getter
@Setter
@NoArgsConstructor
public class CardBindRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Внутренний номер заказа (передаётся в Альфа Банк).
    @Column(nullable = false, unique = true, length = 64)
    private String orderNumber;

    // orderId от Альфа Банка — приходит в ответе register.do.
    @Column(length = 64)
    private String alfaOrderId;

    // PENDING → COMPLETED / FAILED
    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
    }
}

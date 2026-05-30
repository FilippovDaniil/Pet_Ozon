package com.example.marketplace.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Привязанная карта пользователя.
 *
 * Сохраняется автоматически после первой успешной оплаты через Альфа Банк
 * (полная оплата или первый BNPL-депозит). bindingId используется
 * для последующих списаний без ввода данных карты.
 */
@Entity
@Table(name = "card_bindings")
@Getter
@Setter
@NoArgsConstructor
public class CardBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Владелец карты.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Идентификатор привязки в системе Альфа Банка.
    @Column(nullable = false, unique = true, length = 128)
    private String bindingId;

    // Маскированный номер карты, например "411111**1111".
    @Column(length = 20)
    private String maskedPan;

    // Срок действия в формате MMYYYY, например "122025".
    @Column(length = 6)
    private String expiry;

    // Признак карты по умолчанию для автосписаний.
    @Column(nullable = false)
    private boolean isDefault = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
    }

    /** Возвращает срок в читаемом формате MM/YYYY. */
    public String getExpiryFormatted() {
        if (expiry == null || expiry.length() < 6) return expiry;
        return expiry.substring(0, 2) + "/" + expiry.substring(2);
    }
}

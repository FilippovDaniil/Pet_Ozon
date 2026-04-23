package com.example.marketplace.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Корзина покупателя.
 *
 * Связи:
 *   OneToOne  с User    — у каждого пользователя ровно одна корзина.
 *   OneToMany с CartItem — корзина содержит список позиций.
 *
 * Корзина создаётся при регистрации (UserService.registerClient)
 * и при инициализации тестовых данных (AppConfig).
 * После оформления заказа (checkout) корзина очищается: items.clear().
 */
@Entity
@Table(name = "carts")
@Getter
@Setter
@NoArgsConstructor
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // unique = true в @JoinColumn гарантирует: один пользователь — одна корзина (OneToOne).
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // cascade = ALL    — операции (save, delete...) на Cart каскадно применяются к CartItem.
    // orphanRemoval    — если CartItem удалить из списка items, JPA удалит его из БД тоже.
    // mappedBy = "cart" — сторона-владелец связи находится в CartItem.cart (там есть @JoinColumn).
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

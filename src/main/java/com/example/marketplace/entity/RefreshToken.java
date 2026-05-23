package com.example.marketplace.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Refresh-токен — долгоживущий токен для получения нового access-токена.
 *
 * Логика rotation (ротации):
 *   При каждом вызове /api/auth/refresh старый токен удаляется из БД,
 *   а новая пара (access + refresh) выпускается заново.
 *   Это защищает от повторного использования украденного refresh-токена.
 *
 * Время жизни: 7 дней (задаётся в application.properties: jwt.refresh-expiration).
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Случайный UUID — непредсказуемое значение, которое клиент хранит и отправляет.
    @Column(nullable = false, unique = true)
    private String token;

    // Владелец токена. FetchType.LAZY — User не загружается, пока не обратятся к нему.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Момент истечения токена. Instant удобен для сравнения с Instant.now().
    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}

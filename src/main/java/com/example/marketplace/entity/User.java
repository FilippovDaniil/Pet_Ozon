package com.example.marketplace.entity;

import com.example.marketplace.entity.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Пользователь маркетплейса.
 *
 * Реализует интерфейс UserDetails — это контракт Spring Security.
 * Благодаря ему Spring Security умеет работать напрямую с нашей сущностью:
 * загружать её через UserDetailsServiceImpl и аутентифицировать по паролю.
 *
 * @Entity      — JPA будет отображать этот класс в таблицу БД.
 * @Table       — задаёт имя таблицы (по умолчанию было бы "user", но это зарезервированное
 *               слово в PostgreSQL, поэтому явно указываем "users").
 * @Getter, @Setter, @NoArgsConstructor — Lombok генерирует код за нас:
 *   геттеры, сеттеры и конструктор без аргументов (нужен JPA).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User implements UserDetails {

    // @Id — первичный ключ таблицы.
    // @GeneratedValue(IDENTITY) — значение генерирует сама БД (SERIAL / AUTO_INCREMENT).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // unique = true → уникальный индекс в БД; nullable = false → NOT NULL в DDL.
    @Column(unique = true, nullable = false)
    private String email;

    // Хранится хэш BCrypt, никогда не открытый пароль.
    @Column(nullable = false)
    private String password;

    private String fullName;

    // Заполняется только для SELLER — название магазина.
    private String shopName;

    // Адрес доставки — заполняется через /api/profile.
    private String address;

    // Баланс продавца: пополняется автоматически при каждой оплате заказа.
    // precision=15, scale=2 → хранит числа вида 9_999_999_999_999.99
    @Column(precision = 15, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    // EnumType.STRING — в БД хранится текст ("CLIENT"), а не число (0).
    // Это надёжнее: порядок значений enum можно менять без последствий.
    @Enumerated(EnumType.STRING)
    private Role role;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- UserDetails: методы, которые требует Spring Security ---

    /**
     * Возвращает список прав (ролей) пользователя.
     * Spring Security ожидает формат "ROLE_XXX".
     * Поэтому role CLIENT → "ROLE_CLIENT", ADMIN → "ROLE_ADMIN" и т.д.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /**
     * Spring Security использует этот метод как "логин".
     * У нас логин — это email, поэтому возвращаем его.
     */
    @Override
    public String getUsername() {
        return email;
    }

    // Следующие четыре метода Spring Security вызывает при аутентификации.
    // Мы пока не реализуем блокировку и истечение, поэтому всегда true.
    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }

    // --- JPA Lifecycle Callbacks ---

    // @PrePersist вызывается JPA автоматически перед первым INSERT.
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    // @PreUpdate вызывается JPA автоматически перед каждым UPDATE.
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

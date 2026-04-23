package com.example.marketplace.entity.enums;

/**
 * Роли пользователей в системе.
 *
 * Хранится в БД как строка ("CLIENT", "SELLER", "ADMIN") благодаря
 * @Enumerated(EnumType.STRING) в сущности User.
 * Если бы была @Enumerated(EnumType.ORDINAL) — хранился бы порядковый номер (0, 1, 2),
 * что опасно: добавление нового значения в середине сломало бы данные.
 *
 * Spring Security использует эти роли через "ROLE_" + role.name() — см. User.getAuthorities().
 * Поэтому в SecurityConfig правило .hasRole("ADMIN") соответствует "ROLE_ADMIN".
 */
public enum Role {
    CLIENT,  // обычный покупатель
    SELLER,  // продавец, управляет своими товарами
    ADMIN    // администратор, полный доступ
}

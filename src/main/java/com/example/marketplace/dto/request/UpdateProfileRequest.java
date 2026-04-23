package com.example.marketplace.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * Тело запроса PATCH /api/profile/me.
 *
 * Все поля необязательны: клиент отправляет только то, что хочет изменить.
 * Null-поля в UserService.updateProfile() игнорируются — обновляется только то, что пришло.
 * Это называется «частичное обновление» (PATCH-семантика).
 *
 * Пример: обновить только адрес:
 *   PATCH /api/profile/me
 *   {"address": "Москва, ул. Ленина, 1"}
 */
@Getter
@Setter
public class UpdateProfileRequest {
    private String fullName;
    private String address;
    private String shopName;
}

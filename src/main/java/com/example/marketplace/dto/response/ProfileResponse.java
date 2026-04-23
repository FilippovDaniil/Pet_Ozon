package com.example.marketplace.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Ответ на GET /api/profile/me и PATCH /api/profile/me.
 *
 * Включает баланс — актуально для продавцов.
 * Для покупателей balance = 0 (не используется в текущей логике).
 * shopName = null для CLIENT и ADMIN.
 */
@Data
public class ProfileResponse {
    private Long id;
    private String email;
    private String fullName;
    private String address;
    private String role;      // "CLIENT", "SELLER", "ADMIN"
    private String shopName;  // только для SELLER
    private BigDecimal balance;
}

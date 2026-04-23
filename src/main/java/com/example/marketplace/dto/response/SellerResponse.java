package com.example.marketplace.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Ответ на GET /api/seller/balance — данные продавца с балансом.
 *
 * Похож на ProfileResponse, но специализирован для продавца.
 * Баланс — накопленная выручка от продаж, зачисляется в InvoiceService.pay().
 */
@Data
public class SellerResponse {
    private Long id;
    private String email;
    private String fullName;
    private String shopName;
    private BigDecimal balance;  // выручка продавца, накопленная с момента регистрации
}

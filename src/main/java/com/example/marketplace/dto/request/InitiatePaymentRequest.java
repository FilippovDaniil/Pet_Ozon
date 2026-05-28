package com.example.marketplace.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Тело запроса на инициацию BNPL-рассрочки.
 * Используется в POST /api/invoices/{id}/bnpl.
 */
public record InitiatePaymentRequest(

        @NotNull(message = "Укажите продукт рассрочки: BIWEEKLY_4, MONTHLY_4, MONTHLY_6")
        String bnplProduct
) {}

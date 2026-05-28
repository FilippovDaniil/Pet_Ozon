package com.example.marketplace.dto.request;

import jakarta.validation.constraints.Positive;

/**
 * Запрос на досрочную оплату взноса(ов).
 * Если amountKopecks не указан — оплачивается ближайший взнос.
 * Если указан — покрывает столько взносов, сколько помещается в сумму.
 */
public record PayInstallmentRequest(
        @Positive(message = "Сумма должна быть положительной")
        Long amountKopecks   // null → оплатить ближайший PENDING взнос
) {}

package com.example.marketplace.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Тело запроса POST /api/invoice/{id}/pay.
 *
 * paymentMethod — свободная строка: "CARD", "SBP", "CASH" и т.д.
 * В будущем можно заменить на enum PaymentMethod для строгой типизации.
 */
@Data
public class PaymentRequest {
    @NotBlank(message = "Способ оплаты обязателен")
    private String paymentMethod;
}

package com.example.marketplace.dto.response;

/**
 * Одна транзакция (платёж) по BNPL-контракту — для отображения истории платежей.
 */
public record BnplPaymentResponse(
        Long   id,
        Long   amountKopecks,
        String method,        // FIRST | SCHEDULED | MANUAL
        String description,
        String alfaOrderId,
        String paidAt
) {}

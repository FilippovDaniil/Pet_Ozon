package com.example.marketplace.dto.response;

/**
 * Ответ на инициацию оплаты (полной или BNPL).
 * Клиент должен перенаправить пользователя на formUrl.
 */
public record PaymentInitResponse(
        String formUrl,
        String alfaOrderId,
        Long   contractId    // null для полной оплаты; id BnplContract для BNPL
) {}

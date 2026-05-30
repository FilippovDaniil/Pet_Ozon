package com.example.marketplace.entity.enums;

/**
 * Статус платёжной операции в шлюзе Альфа Банка (наша внутренняя проекция).
 *
 * Значения маппятся на числовые коды orderStatus из getOrderStatusExtended.do:
 * APPROVED=1, DEPOSITED=2, REVERSED=3, REFUNDED=4, FAILED=6.
 * PENDING и FORM_SHOWN — наши промежуточные состояния до ответа банка.
 */
public enum AlfaBankOrderStatus {
    PENDING,    // запись создана, форма ещё не открыта
    FORM_SHOWN, // клиент перешёл на форму банка
    APPROVED,   // pre-auth успешен (статус Альфа 1)
    DEPOSITED,  // деньги списаны (статус Альфа 2)
    REVERSED,   // авторизация отменена (статус Альфа 3)
    REFUNDED,   // возврат выполнен (статус Альфа 4)
    FAILED      // оплата отклонена (статус Альфа 6)
}

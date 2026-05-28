package com.example.marketplace.entity.enums;

public enum AlfaBankOrderStatus {
    PENDING,    // запись создана, форма ещё не открыта
    FORM_SHOWN, // клиент перешёл на форму банка
    APPROVED,   // pre-auth успешен (статус Альфа 1)
    DEPOSITED,  // деньги списаны (статус Альфа 2)
    REVERSED,   // авторизация отменена (статус Альфа 3)
    REFUNDED,   // возврат выполнен (статус Альфа 4)
    FAILED      // оплата отклонена (статус Альфа 6)
}

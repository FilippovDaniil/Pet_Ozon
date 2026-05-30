package com.example.marketplace.entity.enums;

/**
 * Статус жизненного цикла BNPL-контракта (рассрочки).
 *
 * Поток: AWAITING_PAYMENT → ACTIVE → COMPLETED.
 * Ветка отмены: AWAITING_PAYMENT/ACTIVE → CANCELLED.
 */
public enum BnplContractStatus {
    AWAITING_PAYMENT,  // pre-auth создан, ждём прохождения формы банка
    ACTIVE,            // первый взнос депозирован, график запущен
    COMPLETED,         // все взносы погашены
    CANCELLED          // все товары отменены / платёж отклонён
}
